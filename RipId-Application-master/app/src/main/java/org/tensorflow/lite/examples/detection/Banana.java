package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.tensorflow.lite.examples.detection.cv.Classifier;
import org.tensorflow.lite.examples.detection.cv.ImageUtil;
import org.tensorflow.lite.examples.detection.cv.Recognition;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Banana extends Activity {

    private static final String CAMERA_ID = "0";
    private static final int PERMISSIONS_MULTIPLE_REQUEST = 123;
    private static final int INPUT_SIZE = org.tensorflow.lite.examples.detection.AsyncClassifierLoader.INPUT_SIZE;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "Banana";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Classifier classifier;

    //------------------------------------------------------------
    private TextView percentageUnripe;
    private TextView percentageRipe;
    private TextView percentageOverripe;
    private ProgressBar progressRipeness;

    private AtomicLong nextUpdate = new AtomicLong(0);

    private org.tensorflow.lite.examples.detection.AutoFitTextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader previewReader;

    private Size previewSize;
    private Size surfaceSize;


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Banana.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }


        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            Banana.this.cameraDevice = null;
        }


        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            Banana.this.cameraDevice = null;
            Banana.this.finish();
        }
    };
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            tryOpenCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {

        }
    };

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {

        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth() / 10;
        int h = aspectRatio.getHeight() / 10;
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w
                    && option.getWidth() <= width
                    && option.getHeight() <= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            System.out.println("Size Error !!!");
            return choices[0];
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("state", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.banana);

        //=================================
        this.textureView = findViewById(R.id.texture);
        this.textureView.setSurfaceTextureListener(mSurfaceTextureListener);


        this.percentageUnripe   = findViewById(R.id.bananaPercentageUnripe);
        this.percentageRipe     = findViewById(R.id.bananaPercentageRipe);
        this.percentageOverripe = findViewById(R.id.bananaPercentageOverripe);
        this.progressRipeness   = findViewById(R.id.bananaRipeValue);

        this.loadClassifier();
    }

    @Override
    protected void onRestart() {
        Log.i("state", "onRestart");
        super.onRestart();
        if (this.surfaceSize != null) {
            this.tryOpenCamera();
        } else {
            Log.e("state", "surfaceSize is invalid, cannot re-start");
            this.finish();
        }
    }

    @Override
    protected void onStart() {
        Log.i("state", "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.i("state", "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i("state", "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i("state", "onStop");
        super.onStop();
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    protected void onDestroy() {
        Log.i("state", "onDestroy");
        super.onDestroy();
        if (classifier != null) {
            // first set to null then close
            // so that the background tasks understands
            Classifier local = classifier;
            classifier = null;
            local.close();
        }
    }

    public void loadClassifier() {
        new org.tensorflow.lite.examples.detection.AsyncClassifierLoader(this, new org.tensorflow.lite.examples.detection.AsyncClassifierLoader.Callback() {
            @Override
            public void onClassifierLoaded(Classifier classifier) {
                Banana.this.classifier = classifier;
            }
        }).execute();
    }

    public void updateConfidenceLevels(float percentageUnripe, float percentageRipe, float percentageOverripe) {

        float progress0 = (percentageUnripe + (1.0f - percentageRipe)) / 2.0f;
        float progress1 = ((1.0f - percentageRipe) + percentageOverripe) / 2.0f;

        float progress = 0.5f - progress0 + progress1;

        //float progress = ((1.0f - percentageUnripe) + percentageRipe*0.5f + percentageOverripe) / (percentageUnripe + percentageOverripe + percentageRipe);

        Banana.this.percentageUnripe    .setText(String.format("%.2f%%", percentageUnripe   * 100.0f));
        Banana.this.percentageRipe      .setText(String.format("%.2f%%", percentageRipe     * 100.0f));
        Banana.this.percentageOverripe  .setText(String.format("%.2f%%", percentageOverripe * 100.0f));
        Banana.this.progressRipeness.setProgress((int)(progress * 100.0f));
        Banana.this.progressRipeness.setMax(100);
    }

    private void tryOpenCamera(int width, int height) {
        this.surfaceSize = new Size(width, height);
        this.tryOpenCamera();
    }
    private void tryOpenCamera() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.CAMERA
                },
                PERMISSIONS_MULTIPLE_REQUEST
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (Manifest.permission.CAMERA.equals(permissions[0]) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.openCamera();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show();
            System.exit(0);
        }
    }

    private void openCamera() {
        this.openCamera(this.surfaceSize.getWidth(), this.surfaceSize.getHeight());
    }

    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (manager != null) {
                try {

                    setUpCameraOutputs(manager, width, height);
                    manager.openCamera(CAMERA_ID, stateCallback, null);

                } catch (CameraAccessException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, "Failed to get CameraManager", Toast.LENGTH_LONG).show();
                Log.e("camera", "Failed to get CameraManager");
            }
        } else {
            Toast.makeText(this, "Missing permissions to open the camera", Toast.LENGTH_LONG).show();
            Log.e("camera", "Missing permissions to open the camera");
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            final CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(previewReader.getSurface());


            cameraDevice.createCaptureSession(
                    Arrays.asList(
                            surface,
                            previewReader.getSurface()
                    ),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {

                            if (null == cameraDevice) {
                                return;
                            }


                            captureSession = cameraCaptureSession;
                            try {

                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                captureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        null
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(Banana.this, "Config Failed!"
                                    , Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs(@NonNull CameraManager manager, int width, int height) {
        try {

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(CAMERA_ID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                    new CompareSizesByArea()
            );

            Log.i("camera", "largest raw: " + largest);

            // TODO otherwise it will not work on newish devices, upper limit not known yet
            largest = new Size(
                    Math.min(largest.getWidth(), INPUT_SIZE * 2),
                    Math.min(largest.getHeight(), INPUT_SIZE * 2)
            );
            Log.i("camera", "largest new: " + largest);

            previewReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.YUV_420_888, 5);
            previewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireNextImage();

                    if (System.currentTimeMillis() > nextUpdate.get() && Banana.this.classifier != null) {
                        nextUpdate.set(Long.MAX_VALUE);
                        new ClassifierTask().execute(image);
                    } else {
                        image.close();
                    }

                }
            }, null);


            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.e(TAG, "NullPointer", e);
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    class ClassifierTask extends AsyncTask<Image, Void, List<Recognition>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected List<Recognition> doInBackground(Image... images) {
            Image image = images[0];
            final YuvImage yuvImage = new YuvImage(ImageUtil.getDataFromImage(image, ImageUtil.COLOR_FormatNV21), ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream outBitmap = new ByteArrayOutputStream();

            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 95, outBitmap);
            byte[] bytes = outBitmap.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);

            Bitmap croppedBitmap = null;
            try {
                croppedBitmap = ImageUtil.getScaleBitmap(bitmap, INPUT_SIZE);
                croppedBitmap = ImageUtil.rotateBimap(90, croppedBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                return classifier.recognizeImage(croppedBitmap);
            } catch (RuntimeException e) {
                if (Banana.this.classifier == null) {
                    // fine app is being stopped
                    Log.i("classifier", "Ignoring RuntimeException because app is closed: "+ e.getMessage());
                    return Collections.emptyList();
                } else {
                    throw e;
                }
            } finally {
                image.close();
            }

        }

        @Override
        protected void onPostExecute(List<Recognition> results) {
            super.onPostExecute(results);
            nextUpdate.set(System.currentTimeMillis() + 500);
            float percentageUnripe      = Float.NaN;
            float percentageRipe        = Float.NaN;
            float percentageOverripe    = Float.NaN;

            for (Recognition recognition : results) {
                switch (recognition.getTitle()) {
                    case "unripen":
                        percentageUnripe = recognition.getConfidence();
                        break;
                    case "ripe":
                        percentageRipe = recognition.getConfidence();
                        break;
                    case "overripe":
                        percentageOverripe = recognition.getConfidence();
                        break;
                }
            }

            Banana.this.updateConfidenceLevels(percentageUnripe, percentageRipe, percentageOverripe);
        }
    }

}