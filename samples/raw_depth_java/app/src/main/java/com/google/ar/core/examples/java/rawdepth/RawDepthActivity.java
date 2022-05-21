/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.rawdepth;

import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to use ARCore Raw Depth API. The application will display
 * a 3D point cloud and allow the user control the number of points based on depth confidence.
 */
public class RawDepthActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = RawDepthActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

  // This lock prevents accessing the frame images while Session is paused.
  private final Object frameInUseLock = new Object();

  /** The current raw depth image timestamp. */
  private long depthTimestamp = -1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up rendering.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 0, 16, 0);
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);

    // Set up confidence threshold slider.
    SeekBar seekBar = findViewById(R.id.slider);
    seekBar.setProgress(seekBar.getMax());
    seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

    installRequested = false;
  }

  private SeekBar.OnSeekBarChangeListener seekBarChangeListener =
      new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
      };

  @Override
  protected void onDestroy() {
    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;

      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        exception = e;
      } catch (RuntimeException e) {
        exception = e;
      }

      if (!session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)) {
        session = null;
      }

      if (exception != null) {
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      // Wait until the frame is no longer being processed.
      synchronized (frameInUseLock) {
        // Enable raw depth estimation and auto focus mode while ARCore is running.
        Config config = session.getConfig();
        config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
        session.configure(config);
        session.resume();
      }
    } catch (CameraNotAvailableException e) {
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - see note in onResume().
      // GLSurfaceView is paused before pausing the ARCore session, to prevent onDrawFrame() from
      // calling session.update() on a paused session.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }

    // Synchronize prevents session.update() call while paused, see note in onPause().
    synchronized (frameInUseLock) {
      // Notify ARCore that the view size changed so that the perspective matrix can be adjusted.
      displayRotationHelper.updateSessionIfNeeded(session);

      try {
        session.setCameraTextureNames(new int[] {0});

        Frame frame = session.update();
        Camera camera = frame.getCamera();

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        if (camera.getTrackingState() != TrackingState.TRACKING) {
          // If motion tracking is not available but previous depth is available, notify the user
          // that the app will resume with tracking.

          // If not tracking, do not render the point cloud.
          return;
        }

        // Check if the frame contains new depth data or a 3D reprojection of the previous data. See
        // documentation of acquireRawDepthImage16Bits for more details.
        boolean containsNewDepthData;
        try (Image depthImage = frame.acquireRawDepthImage16Bits()) {
          containsNewDepthData = depthTimestamp == depthImage.getTimestamp();
          depthTimestamp = depthImage.getTimestamp();
        } catch (NotYetAvailableException e) {
          // This is normal at the beginning of session, where depth hasn't been estimated yet.
          containsNewDepthData = false;
        }

        if (containsNewDepthData) {
          // Get Raw Depth data of the current frame.
          final DepthData depth = DepthData.create(session, frame);
        }

        float[] projectionMatrix = new float[16];
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
        float[] viewMatrix = new float[16];
        camera.getViewMatrix(viewMatrix, 0);
      } catch (Throwable t) {
        // Avoid crashing the application due to unhandled exceptions.
        Log.e(TAG, "Exception on the OpenGL thread", t);
      }
    }
  }
}
