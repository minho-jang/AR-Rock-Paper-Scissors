/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SkeletonNode;
import com.google.ar.sceneform.animation.ModelAnimator;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.AnimationData;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    /////////////////////////////////////////////////// animation 복붙
    private static final String TAG = "AnimationSample";
    private static final int ANDY_RENDERABLE = 1;
    private static final int HAT_RENDERABLE = 2;
    private static final String HAT_BONE_NAME = "hat_point";
    public boolean imageReady = false;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;
    private Classifier detector;
    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;
    private ArFragment arFragment;
    // Model loader class to avoid leaking the activity context.
    private ModelLoader modelLoader;
    private ModelRenderable andyRenderable;
    private AnchorNode anchorNode;
    private SkeletonNode andy;
    // Controls animation playback.
    private ModelAnimator animator;
    // Index of the current animation playing.
    private int nextAnimation;
    // The UI to play next animation.
    private FloatingActionButton animationButton;
    // The UI to toggle wearing the hat.
    private FloatingActionButton hatButton;
    private Node hatNode;
    private ModelRenderable hatRenderable;
    // Detection Button.
    private FloatingActionButton detectionButton;
    //현재 사용자의 손 상태
    private String currentMyHand;
    //현재 detect 정확도
    private Float handAccuracy;
    //비동기
    private AsyncTask<Void, Integer, Void> mTask;

    private ModelAnimator Handanimator;
    private int handAnimaitonIndex = 1;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_camera);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arcore_fragment);

        modelLoader = new ModelLoader(this);

        modelLoader.loadModel(ANDY_RENDERABLE, R.raw.andy_dance);
        // modelLoader.loadModel(HAT_RENDERABLE, R.raw.baseball_cap);
        modelLoader.loadModel(HAT_RENDERABLE, R.raw.hand_1);


        // When a plane is tapped, the model is placed on an Anchor node anchored to the plane.
        arFragment.setOnTapArPlaneListener(this::onPlaneTap);

        // Add a frame update listener to the scene to control the state of the buttons.
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onFrameUpdate);

        // Once the model is placed on a plane, this button plays the animations.
        animationButton = findViewById(R.id.animate);
        animationButton.setEnabled(false);
        animationButton.setOnClickListener(this::onPlayAnimation);

        // Place or remove a hat on Andy's head showing how to use Skeleton Nodes.
        hatButton = findViewById(R.id.hat);
        hatButton.setEnabled(false);
        hatButton.setOnClickListener(this::onToggleHat);

        detectionButton = findViewById(R.id.detection);
        detectionButton.setEnabled(false);
        detectionButton.setOnClickListener(this::onDetection);
    }

    private void onDetection(View unusedView) {
        Log.d("TEST", "Detection Button is clicked");
        imageReady = true;

        // 바로 애니메이션 실행
        if (animator == null || !animator.isRunning()) {
            AnimationData data = andyRenderable.getAnimationData(1);
            animator = new ModelAnimator(data, andyRenderable);
            animator.start();

            mTask = new AsyncTask<Void, Integer, Void>(){
                @Override
                protected Void doInBackground(Void... params)
                {
                    try
                    {
                        Log.d("TAG","lets sleep");
                        //5초간 sleep
                        Thread.sleep(5*1000);
                    }
                    catch(InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    return null;
                }
                // 작업 완료 직후에 호출되는 메소드
                @Override
                protected void onPostExecute(Void result)
                {
                    Log.d("TAG","start animation 2");
                    AnimationData data = andyRenderable.getAnimationData(2);
                    animator = new ModelAnimator(data, andyRenderable);
                    animator.start();
                }

                // 외부에서 강제로 취소할때 호출되는 메소드
                @Override
                protected void onCancelled()
                {
                    Log.d("TAG","async task is cancealed");
                }
            };
            mTask.execute();

            Toast toast = Toast.makeText(this, data.getName(), Toast.LENGTH_SHORT);
            Log.d(
                    TAG,
                    String.format(
                            "Starting animation %s - %d ms long", data.getName(), data.getDurationMs()));
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }

        handAnimaitonIndex++;
    }

    private void onPlayAnimation(View unusedView) {
        if (animator == null || !animator.isRunning()) {
            AnimationData data = andyRenderable.getAnimationData(nextAnimation);
            nextAnimation = (nextAnimation + 1) % andyRenderable.getAnimationDataCount();
            animator = new ModelAnimator(data, andyRenderable);
            animator.start();
            Toast toast = Toast.makeText(this, data.getName(), Toast.LENGTH_SHORT);
            Log.d(
                    TAG,
                    String.format(
                            "Starting animation %s - %d ms long", data.getName(), data.getDurationMs()));
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }
    }

    /*
     * Used as the listener for setOnTapArPlaneListener.
     */
    private void onPlaneTap(HitResult hitResult, Plane unusedPlane, MotionEvent unusedMotionEvent) {
        if (andyRenderable == null || hatRenderable == null) {
            return;
        }
        // Create the Anchor.
        Anchor anchor = hitResult.createAnchor();

        if (anchorNode == null) {
            anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            andy = new SkeletonNode();

            andy.setParent(anchorNode);
            andy.setRenderable(andyRenderable);
            hatNode = new Node();

            // Attach a node to the bone.  This node takes the internal scale of the bone, so any
            // renderables should be added to child nodes with the world pose reset.
            // This also allows for tweaking the position relative to the bone.
            Node boneNode = new Node();
            boneNode.setParent(andy);
            andy.setBoneAttachment(HAT_BONE_NAME, boneNode);
            hatNode.setRenderable(hatRenderable);
            hatNode.setParent(boneNode);
            hatNode.setWorldScale(new Vector3(0.2f, 0.2f, 0.2f));
            hatNode.setWorldRotation(Quaternion.identity());
            Vector3 pos = hatNode.getWorldPosition();

            // Lower the hat down over the antennae.
            pos.y -= .1f;

            hatNode.setWorldPosition(pos);
        }
    }

    /**
     * Called on every frame, control the state of the buttons.
     *
     * @param unusedframeTime
     */
    private void onFrameUpdate(FrameTime unusedframeTime) {

//        if (imageReady) {
//            onImageAvailable(null);
//        }

        // If the model has not been placed yet, disable the buttons.
        if (anchorNode == null) {
            if (animationButton.isEnabled()) {
                animationButton.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.GRAY));
                animationButton.setEnabled(false);
                hatButton.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.GRAY));
                hatButton.setEnabled(false);
            }
        } else {
            if (!animationButton.isEnabled()) {
                animationButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
                animationButton.setEnabled(true);
                hatButton.setEnabled(true);
                hatButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary)));
                detectionButton.setEnabled(true);
                detectionButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary)));
            }
        }
    }

    private void onToggleHat(View unused) {
        if (hatNode != null) {
            hatNode.setEnabled(!hatNode.isEnabled());

            // Set the state of the hat button based on the hat node.
            if (hatNode.isEnabled()) {
                hatButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary)));
            } else {
                hatButton.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
            }
        }
    }

    void setRenderable(int id, ModelRenderable renderable) {
        if (id == ANDY_RENDERABLE) {
            this.andyRenderable = renderable;
        } else {
            this.hatRenderable = renderable;
        }
    }

    void onException(int id, Throwable throwable) {
        Toast toast = Toast.makeText(this, "Unable to load renderable: " + id, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
        Log.e(TAG, "Unable to load andy renderable", throwable);
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        handAccuracy = results.get(0).getConfidence();

                        //최대 정확도가 50% 아래면 저장 안하고 50% 위면 현재 상태 반영
                        if(handAccuracy < 0.5){
                            currentMyHand = "under 50percent";
                            Log.d(TAG, "현재 상태 : " + currentMyHand + "정확도 : " + handAccuracy);
                        }else{
                            currentMyHand = results.get(0).getTitle();
                            Log.d(TAG, "현재 상태 : " + currentMyHand + "정확도 : " + handAccuracy);
                        }

//                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
//                        final Canvas canvas = new Canvas(cropCopyBitmap);
//                        final Paint paint = new Paint();
//                        paint.setColor(Color.RED);
//                        paint.setStyle(Style.STROKE);
//                        paint.setStrokeWidth(2.0f);
//
//                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
//                        switch (MODE) {
//                            case TF_OD_API:
//                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
//                                break;
//                        }
//
//                        final List<Classifier.Recognition> mappedRecognitions =
//                                new LinkedList<Classifier.Recognition>();
//
//                        String temp_maxscore=null;
//
//                        for (final Classifier.Recognition result : results) {
//                            final RectF location = result.getLocation();
//                            if (location != null && result.getConfidence() >= minimumConfidence) {
//
//                                //canvas.drawRect(location, paint);
//                                //Log.d(TAG,result.toString()) ;
//
//                                cropToFrameTransform.mapRect(location);
//
//                                result.setLocation(location);
//                                mappedRecognitions.add(result);
//                            }
//                        }
//
//                        tracker.trackResults(mappedRecognitions, currTimestamp);
//                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        // 밑에 Toolbar 없애면서 잠시 주석처리
//                        runOnUiThread(
//                                new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        showFrameInfo(previewWidth + "x" + previewHeight);
//                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
//                                        showInference(lastProcessingTimeMs + "ms");
//                                    }
//                                });
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }
}
