package com.example.facedetection;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.disposables.Disposable;

import static android.content.Context.CAMERA_SERVICE;

/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment {
    private static final String TAG = MainFragment.class.getSimpleName();
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @BindView(R.id.view_finder)
    TextureView viewFinder;
    @BindView(R.id.captureFab)
    FloatingActionButton captureFab;
    @BindView(R.id.rectangleView)
    GraphicOverlay graphicOverlay;
    private Unbinder unbinder;


    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        RxPermissions rxPermissions = new RxPermissions(this);
        Disposable pDisposable = rxPermissions.request(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
                .subscribe(isPermitted -> {
                    if (isPermitted) {
                        viewFinder.post(this::startCamera);
                    } else {
                        Toast.makeText(getActivity(), "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                    }
                }, Throwable::printStackTrace);


        viewFinder.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            updateTransform();
        });


    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    private void startCamera() {

        try {

            // Get screen metrics used to setup camera for full screen resolution
            Rational screenAspectRatio = new Rational(viewFinder.getWidth(), viewFinder.getHeight());

            Log.d(TAG, "startCamera: " + viewFinder.getWidth() + ":" + viewFinder.getHeight());

            // Create configuration object for the viewfinder use case
            PreviewConfig previewConfig = new PreviewConfig.Builder()
                    .setLensFacing(CameraX.LensFacing.FRONT)
                    .setTargetAspectRatio(screenAspectRatio)
                    .setTargetResolution(new Size(viewFinder.getWidth(), viewFinder.getHeight()))
                    .build();


            // Build the viewfinder use case
            Preview preview = new Preview(previewConfig);

            // Every time the viewfinder is updated, recompute layout
            preview.setOnPreviewOutputUpdateListener(output -> {
                ViewGroup parent = (ViewGroup) viewFinder.getParent();
                parent.removeView(viewFinder);
                parent.addView(viewFinder, 0);

                viewFinder.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            });


            // Create configuration object for the image capture use case
            ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                    .setTargetAspectRatio(screenAspectRatio)
                    .setLensFacing(CameraX.LensFacing.FRONT)
                    // We don't set a resolution for image capture; instead, we
                    // select a capture mode which will infer the appropriate
                    // resolution based on aspect ration and requested mode
                    .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                    .build();

            // Build the image capture use case and attach button click listener
            ImageCapture imageCapture = new ImageCapture(imageCaptureConfig);

            captureFab.setOnClickListener(v -> {
                File file = new File(AppController.getInstance().getExternalMediaDirs()[0], System.currentTimeMillis() + ".jpg");

                imageCapture.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {
                        Log.d(TAG, "onImageSaved: " + file.getPath());
                        Toast.makeText(getActivity(), "Image Saved", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.UseCaseError useCaseError, @NonNull String message, @Nullable Throwable cause) {
                        Toast.makeText(getActivity(), "Photo capture failed", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "onError: " + message);
                        cause.printStackTrace();
                    }
                });
            });


            // Setup image analysis pipeline
            ImageAnalysisConfig analyzerConfig = new ImageAnalysisConfig.Builder()
                    .setLensFacing(CameraX.LensFacing.FRONT)
                    .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                    .build();

            @SuppressLint("RestrictedApi") int rotation = getRotationCompensation(CameraX.getCameraWithLensFacing(CameraX.LensFacing.FRONT), getActivity(), getActivity());

            // Build the image analysis use case and instantiate our analyzer
            ImageAnalysis imageAnalysis = new ImageAnalysis(analyzerConfig);
            imageAnalysis.setAnalyzer(new MyImageAnalyzer(rotation) {
                @Override
                public void onDesiredImageFoundStatus(boolean isFound, int imageWidth, int imageHeight, List<Rect> faceRects) {

                    graphicOverlay.clear();
                    graphicOverlay.setCameraInfo(imageHeight, imageWidth, CameraX.LensFacing.FRONT);

                    if (isFound) {
                        captureFab.setVisibility(View.VISIBLE);

                        for (Rect faceRect : faceRects) {
                            Log.d(TAG, "onDesiredImageFoundStatus: " + faceRect.left);
                            RectOverlay rectOverlay = new RectOverlay(graphicOverlay, faceRect);
                            graphicOverlay.add(rectOverlay);
                        }
                    } else
                        captureFab.setVisibility(View.GONE);

                }
            });


            // Bind use cases to lifecycle
            CameraX.bindToLifecycle(this, preview, imageCapture, imageAnalysis);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();

        // Compute the center of the view finder
        float centerX = viewFinder.getWidth() / 2f;
        float centerY = viewFinder.getHeight() / 2f;

        // Correct preview output to account for display rotation

        float rotationDegrees = 0f;
        switch (viewFinder.getDisplay().getRotation()) {
            case Surface.ROTATION_0:
                rotationDegrees = 0f;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 9f;
                break;
            case Surface.ROTATION_180:
                rotationDegrees = 180f;
                break;
            case Surface.ROTATION_270:
                rotationDegrees = 270f;
                break;
        }


        matrix.postRotate(rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix);
    }

    private int getRotationCompensation(String cameraId, Activity activity, Context context) throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // On most devices, the sensor orientation is 90 degrees, but for some
        // devices it is 270 degrees. For devices with a sensor orientation of
        // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.
        CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);
        rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;

        // Return the corresponding FirebaseVisionImageMetadata rotation value.
        int result;
        switch (rotationCompensation) {
            case 0:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                break;
            case 90:
                result = FirebaseVisionImageMetadata.ROTATION_90;
                break;
            case 180:
                result = FirebaseVisionImageMetadata.ROTATION_180;
                break;
            case 270:
                result = FirebaseVisionImageMetadata.ROTATION_270;
                break;
            default:
                result = FirebaseVisionImageMetadata.ROTATION_0;
                Log.e(TAG, "Bad rotation value: " + rotationCompensation);
        }
        return result;
    }

}
