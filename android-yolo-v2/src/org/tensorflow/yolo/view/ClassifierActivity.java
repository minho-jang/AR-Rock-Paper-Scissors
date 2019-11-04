package org.tensorflow.yolo.view;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
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

import org.tensorflow.yolo.R;
import org.tensorflow.yolo.TensorFlowImageRecognizer;
import org.tensorflow.yolo.model.Recognition;
import org.tensorflow.yolo.util.ImageUtils;
import org.tensorflow.yolo.view.components.BorderedText;

import java.util.List;
import java.util.Vector;

import static org.tensorflow.yolo.Config.INPUT_SIZE;
import static org.tensorflow.yolo.Config.LOGGING_TAG;

/**
 * Classifier activity class
 * Modified by Zoltan Szabo
 */
public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
    private boolean MAINTAIN_ASPECT = true;
    private float TEXT_SIZE_DIP = 10;

    private TensorFlowImageRecognizer recognizer;
    private Integer sensorOrientation;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private Bitmap croppedBitmap = null;
    private boolean computing = false;
    private Matrix frameToCropTransform;

    private OverlayView overlayView;
    private BorderedText borderedText;
    private long lastProcessingTimeMs;

    ///////////////////////////// My Space //////////////////////////////////////////////

    private static final String TAG = "AnimationSample";
    private static final int ANDY_RENDERABLE = 1;
    private static final int HAT_RENDERABLE = 2;
    private static final String HAT_BONE_NAME = "hat_point";
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
    private boolean imageReady=false;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_camera);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arcore_fragment);

        modelLoader = new ModelLoader(this);

        modelLoader.loadModel(ANDY_RENDERABLE, R.raw.andy_dance);
        modelLoader.loadModel(HAT_RENDERABLE, R.raw.baseball_cap);

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
    }

    ///////////////////////////// My Space ////////////////////////////////////////////////////

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
            hatNode.setWorldScale(Vector3.one());
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

        if(imageReady){

        }

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
            }
        }
    }

    private void onToggleHat(View unused) {
        Image image = null;
        try {
            Frame currentFrame = arFragment.getArSceneView().getArFrame();
            image = currentFrame.acquireCameraImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }

            computing = true;
            fillCroppedBitmap(image);
            image.close();
        } catch (Exception ex) {
            if (image != null) {
                image.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }catch (NoClassDefFoundError ex) {
            if (image != null) {
                image.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }

        // TODO 지금 실행 안되고 있음 !
        Log.d("TEST ClassifierActivity", "runInBackground( recognizer ) 시작");
        runInBackground(() -> {
            final long startTime = SystemClock.uptimeMillis();
            final List<Recognition> results = recognizer.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
            overlayView.setResults(results);
            // speak(results);
            requestRender();
            computing = false;
            Log.d("TEST ClassifierActivity", "결과 : " + results.toString());
        });

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
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        recognizer = TensorFlowImageRecognizer.create(getAssets());

        overlayView = (OverlayView) findViewById(R.id.overlay);
        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();

        Log.i(LOGGING_TAG, String.format("Sensor orientation: %d, Screen orientation: %d",
                rotation, screenOrientation));

        sensorOrientation = rotation + screenOrientation;

        Log.i(LOGGING_TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));

        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(previewWidth, previewHeight,
                INPUT_SIZE, INPUT_SIZE, sensorOrientation, MAINTAIN_ASPECT);
        frameToCropTransform.invert(new Matrix());

        addCallback((final Canvas canvas) -> renderAdditionalInformation(canvas));

        imageReady=true;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;

        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }

            computing = true;
            fillCroppedBitmap(image);
            image.close();
        } catch (final Exception ex) {
            if (image != null) {
                image.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }

        // TODO 지금 실행 안되고 있음 !
        Log.d("TEST ClassifierActivity", "runInBackground( recognizer ) 시작");
        runInBackground(() -> {
            final long startTime = SystemClock.uptimeMillis();
            final List<Recognition> results = recognizer.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
            overlayView.setResults(results);
            // speak(results);
            requestRender();
            computing = false;
        });
    }

    private void fillCroppedBitmap(final Image image) {
        Bitmap rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        rgbFrameBitmap.setPixels(ImageUtils.convertYUVToARGB(image, previewWidth, previewHeight),
                0, previewWidth, 0, 0, previewWidth, previewHeight);
        new Canvas(croppedBitmap).drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.close();
        }
    }

    private void renderAdditionalInformation(final Canvas canvas) {
        final Vector<String> lines = new Vector();
        if (recognizer != null) {
            for (String line : recognizer.getStatString().split("\n")) {
                lines.add(line);
            }
        }

        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

        borderedText.drawLines(canvas, 10, 10, lines);
    }
}
