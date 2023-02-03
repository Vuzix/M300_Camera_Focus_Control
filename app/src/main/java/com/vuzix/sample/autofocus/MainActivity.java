/*'*****************************************************************************
Copyright (c) 2018, Vuzix Corporation
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

*  Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

*  Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

*  Neither the name of Vuzix Corporation nor the names of
   its contributors may be used to endorse or promote products derived
   from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*'*****************************************************************************/
package com.vuzix.sample.autofocus;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * This sample app shows how to operate the auto focus in Camera API 1 on the M300.
 *
 * The best way to use the auto focus in M300 is to leave the focus alone. The M300's
 * camera platform will default to continuous automatic focus. To keep the image in focus you do
 * not need to make any setting changes or focus calls.
 *
 * If the developers use the Camera API 1 to call the autofocus(), it will potentially cause the
 * auto focus to stop working properly.
 *
 * This sample app also shows how to use the autofocus() correctly with cancelAutoFocus() to create
 * a push-to-focus implementation.  Press the enter key or double-tap the touchpad to focus.
 */
public class MainActivity extends Activity{
    private static final String TAG = "AutoFocus_App";
    private static final int REQUEST_CAMERA_PERMISSION = 200;  // Just needs to be unique within this app

    private CameraPreview cameraPreview;

    /**
     * Setup the view when created
     * @param savedInstanceState - unused, passed to superclass
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraPreview = new CameraPreview(this);
    }

    /**
     * Restart the preview when we resume
     */
    @Override
    protected void onResume() {
        super.onResume();
        cameraPreview.resumePreview();
        // Note: there is no need for a pausePreview. The surface handles that
    }

    /**
     * Trigger to begin the autofocus() process upon pressing the enter key or double-tap the
     * touchpad
     *
     * This example is only for cases where push-to-focus is required. Normally it is NOT necessary
     * to call the focus routines manually, because the platform will do the autofocus automatically
     *
     * @param keyCode The value in event.getKeyCode().
     * @param event Description of the key event.
     * @return return true if the method consume this event
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        switch (keyCode){
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:  // Double-tap the touchpad
                cameraPreview.userRequestToFocus();
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * A SurfaceView sub-class to perform the camera preview and control the focus
     */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback,
            Camera.PreviewCallback, Camera.AutoFocusCallback, RotationListener.rotationCallbackFn {

        private Camera camera;
        private Context context;

        private boolean autoFocusStarted = false;
        private int autoFocusCount = 0;

        private final int AUTOFOCUS_INTERVAL_MILLISECOND = 10;
        private RotationListener rotationLister;

        /**
         * The constructor of CameraPreview
         * @param context the activity context
         */
        public CameraPreview(Context context) {
            super(context);
            this.context = context;
            init();
        }

        /**
         * Camera init method to open the camera
         */
        private void init() {
            Log.d(TAG, "Initializing");
            getHolder().addCallback(this);
            if (!checkCameraPermission()) {
                requestCameraPermission();
            }
        }

        /**
         * Open the camera
         */
        private void openCamera() {
            try {
                Log.d(TAG, "Opening camera");
                rotationLister = new RotationListener();
                camera = Camera.open();

                // These are the simplest two focus options:
                //String newFocusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                //String newFocusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                
                // If either CONTINUOUS_PICTURE or CONTINUOUS_VIDEO had been chosen, there
                // is nothing more you need to do.  Just let Android do the work. If you want to
                // have a single auto-focus, use FOCUS_MODE_AUTO as shown in this example
                String newFocusMode = Camera.Parameters.FOCUS_MODE_AUTO;  // Enables push-to-focus mode
                
                Camera.Parameters params = camera.getParameters();
                if (!newFocusMode.equals(params.getFocusMode())) {
                    Log.d(TAG, "Changing focus from " + params.getFocusMode() + " to " + newFocusMode );
                    params.setFocusMode(newFocusMode);
                    camera.setParameters(params);
                }
                // Now initiate one focus attempt to lock the focus somewhere then notify us when complete
                userRequestToFocus();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Start camera preview including opening the camera if required
         */
        public void startPreview() {
            Log.d(TAG, "Starting preview");
            try {
                if (camera == null) {
                    openCamera();
                }

                updateOrientation();
                camera.setPreviewDisplay(this.getHolder());
                camera.startPreview();
                rotationLister.listen(context, this);
                doAutoFocusInBackground(); // Trigger one auto-focus to lock the focus
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Stop camera preview and release the camera
         */
        public void stopPreview() {
            if (camera != null) {
                Log.d(TAG, "Stop preview");
                rotationLister.stop();
                rotationLister = null;
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }

        /**
         * Resume the camera preview
         */
        public void resumePreview() {
            if ( checkCameraPermission() ) {
                Log.d(TAG, "Resume preview");
                setContentView(this);
                rotationLister = new RotationListener();
            }
        }

        /**
         * The user has clicked the button to switch to app-controlled focus
         */
        public void userRequestToFocus() {
            Log.d(TAG, "Focus requested");
            if(!autoFocusStarted) {
                Log.d(TAG, "focusing");
                getActionBar().setTitle( getString(R.string.app_name) + " - " + getString(R.string.focusing));
                autoFocusStarted = true;
                autoFocusCount = 0;
                doAutoFocusInBackground();
            }
        }

        /**
         * Internal utility method to record when focus has stopped
         */
        private void focusStopped() {
            Log.d(TAG, "focus locked");
            autoFocusStarted = false;
            getActionBar().setTitle( getString(R.string.app_name) + " - " + getString(R.string.locked));
        }

        /**
         * Internal utility method to trigger an auto focus scan.
         *
         * Note: Called only from the async task
         */
        private void internalTriggerFocusStart() {
            Log.d(TAG, "Focus triggered");
            try{
                camera.autoFocus(this);
            } catch (RuntimeException e){
                Log.e(TAG,"internalTriggerFocusStart() " + e);
                focusStopped();
            }
        }

        /**
         * We use an Async Task to trigger auto focus operation after it sleep the interval time
         */
        private final class AutoFocusAsyncTask extends AsyncTask<Object, Object, Object> {

            protected Object doInBackground(Object... voids) {
                try {
                    //if(autoFocusCount < MAX_AUTOFOCUS_COUNT) {
                        autoFocusCount ++;
                        /**
                         * before sleep, please cancel the auto focus first
                         */
                        camera.cancelAutoFocus();
                        Thread.sleep(AUTOFOCUS_INTERVAL_MILLISECOND);
                        Log.d(TAG,"after auto focus sleep " + autoFocusCount);
                        internalTriggerFocusStart();
//                    } else {
//                        focusStopped();
//                        autoFocusCount = 0;
//                        Log.d(TAG, "focus timer expired");
//                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "auto focus interval time failed");
                    focusStopped();
                }
                return null;
            }
        }

        /**
         * Do the auto focus in a background async task
         */
        private void doAutoFocusInBackground() {

            AutoFocusAsyncTask task = new AutoFocusAsyncTask();
            try{
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }catch (RejectedExecutionException e){
                e.printStackTrace();
            }
        }

        /**
         * Auto focus callback method, it call the doAutoFocusInBackground() to trigger the auto
         * focus again later.  In this example, this is being used to focus again after each
         * success or failure of the autofocus mechanism.
         *
         * @param success true if focus was successful, false if otherwise
         * @param camera the Camera service object
         */
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Log.d(TAG, "Focus Callback: " + String.valueOf(success));
            if(success) {
                focusStopped();
            } else {
                doAutoFocusInBackground();
            }
        }


        /**
         * Called as preview frames are displayed. No behavior in this example.
         *
         * @param data byte[]: the contents of the preview frame
         * @param camera Camera: the Camera service object
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
        }

        /**
         * This is called immediately after the surface is first created.
         *
         * @param holder The SurfaceHolder whose surface is being created.
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "Surface created");
            startPreview();
        }

        /**
         * This is called immediately after any structural changes (format or size) have been made to the surface.
         *
         * @param holder The SurfaceHolder whose surface has changed.
         * @param format The new PixelFormat of the surface.
         * @param width The new width of the surface.
         * @param height The new height of the surface.
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

        /**
         * This is called immediately before a surface is being destroyed
         *
         * @param holder The SurfaceHolder whose surface is being destroyed
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "Surface destroyed");
            stopPreview();
        }

        /**
         * The rotation listener's callback to notify the device rotation is changed
         * @param newRotation new value of the rotation
         */
        @Override
        public void onRotationChanged(int newRotation) {
            updateOrientation();
        }

        /**
         * Utility to update the camera display rotation. Call upon creation and orientation changes.
         */
        private void updateOrientation(){
            Display display = ((WindowManager) this.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            int degrees = 0;
            switch (display.getRotation()) {
                case Surface.ROTATION_0:
                    degrees = 180;
                    break;
                case Surface.ROTATION_180:
                    degrees = 0;
                    break;
            }

            if(camera != null)
                camera.setDisplayOrientation(degrees);
        }

    } //nested class CameraPreview

    /**
     * Check whether the app granted the camera permission or not
     *
     * @return return true if the permission granted
     */
    public boolean checkCameraPermission(){
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request to grant the camera permission
     */
    public void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    /**
     * Callback for the result from requesting permissions
     *
     * @param requestCode The request code passed in requestPermissions(android.app.Activity, String[], int)
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Log.d(TAG, "Permission denied. Exiting");
                Toast.makeText(MainActivity.this, "Sorry! Camera permission required", Toast.LENGTH_LONG).show();
                finish();
            }else if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Log.d(TAG, "Permission granted");
                // No need to do anything. The onResume will be processed to show the camera
            }
        }
    }
}
