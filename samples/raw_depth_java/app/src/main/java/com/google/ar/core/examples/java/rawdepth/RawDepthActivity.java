// removed License to keep it simple - see upstream / git log

package com.google.ar.core.examples.java.rawdepth;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class RawDepthActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = RawDepthActivity.class.getSimpleName();

    private Session session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            session = new Session(this);

            Config config = session.getConfig();
            config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
            session.configure(config);

            session.resume();
        } catch (Throwable t) {
            Log.e(TAG, "Exception creating session", t);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (session == null) {
            return;
        }

        try {
            session.update();
        } catch (CameraNotAvailableException e) {
            // Just ignore in this MRE
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
}
