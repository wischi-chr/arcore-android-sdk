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

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Config;
import com.google.ar.core.Session;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to use ARCore Raw Depth API. The application will display
 * a 3D point cloud and allow the user control the number of points based on depth confidence.
 */
public class RawDepthActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = RawDepthActivity.class.getSimpleName();

  private Session session;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    try {
      session = new Session(this);

      // Enable raw depth estimation and auto focus mode while ARCore is running.
      Config config = session.getConfig();
      config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
      session.configure(config);
      session.resume();
    }
    catch (Throwable t) {
      Log.e(TAG, "Exception creating session", t);
      // just to shut up the compiler.
    }
  }

  @Override
  protected void onDestroy() {
    // Not needed to reproduce the bug. App crashes anyway, no need for cleanup ;-)
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    // Pause and Resume is not supported. App is just used to reproduce a bug.
    super.onResume();
  }

  @Override
  public void onPause() {
    // Pause and Resume is not supported. App is just used to reproduce a bug.
    super.onPause();
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    if (session == null) {
      return;
    }

    try {
      session.update();
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }
}
