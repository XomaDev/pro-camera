package com.kumaraswamy.camerax;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UsesPermissions(permissionNames = "android.permission.WRITE_EXTERNAL_STORAGE, android.permission.CAMERA")
@DesignerComponent(version = 1, category = ComponentCategory.EXTENSION,
        description = "Pro custom camera developed by Kumaraswamy B G", nonVisible = true,
        iconName = "https://micode.vercel.app/icon/camera.png")
@SimpleObject(external = true)
public class ProCamera extends AndroidNonvisibleComponent implements View.OnTouchListener {
    private final Activity activity;

    private int cameraType = 0;
    private int noiseReductionMode = 0;
    private int cameraStyle = 0;
    private int faceDetectMode = 0;

    private AutoFitTextureView cameraLayout;
    private Size imageDimension;

    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSessions;

    private boolean flashMode = false;
    private boolean enhance = false;
    private boolean hasZoomSupport = false;
    private boolean visible = true;
    private boolean gestureZoom = false;

    private String cameraID;
    private String imageDestination;

    private View view;
    private Rect rectSensor;
    private final Rect rectCrop = new Rect();

    private float maxZoom;
    private float currentZoom;

    private float finger_spacing = 0;

    private FrameLayout cameraView;

    private static final SparseIntArray ORIENTATIONS;

    static {
        (ORIENTATIONS = new SparseIntArray()).append(0, 0);
        ProCamera.ORIENTATIONS.append(1, 90);
        ProCamera.ORIENTATIONS.append(2, 180);
        ProCamera.ORIENTATIONS.append(3, 270);
    }


    public ProCamera(final ComponentContainer container) {
        super(container.$form());

        activity = container.$context();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(final SurfaceTexture surfaceTexture, final int height, final int width) {
            configureTransform(height, width);
            openCamera();
        }

        public void onSurfaceTextureSizeChanged(final SurfaceTexture surfaceTexture, final int i, final int i1) {
        }

        public boolean onSurfaceTextureDestroyed(final SurfaceTexture surfaceTexture) {
            return false;
        }

        public void onSurfaceTextureUpdated(final SurfaceTexture surfaceTexture) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        public void onOpened(final CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        public void onDisconnected(final CameraDevice camera) {
            cameraDevice.close();
        }

        public void onError(final CameraDevice camera, final int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    @SimpleProperty
    public float Zoom() {
        return currentZoom;
    }

    final CameraCaptureSession.CaptureCallback captureListener =  new CameraCaptureSession.CaptureCallback() {
        public void onCaptureCompleted(final CameraCaptureSession session, final CaptureRequest request, final TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            createCameraPreview();
            final int res = result.get(CaptureResult.CONTROL_AF_STATE);
            if (res == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                FocusLocked();
            } else if (res == CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                FocusUnlocked();
            }
        }
    };

    @SimpleFunction
    public void AddComponent(AndroidViewComponent component) {
        View v = component.getView();
        if(v.getParent() != null) {
            ((ViewGroup)v.getParent()).removeView(v); // <- fix
        }
        FrameLayout componentLayout = new FrameLayout(activity);
        componentLayout.addView(v);
        cameraView.addView(componentLayout);
    }

    @SimpleFunction(description = "Initialize camera in an arrangement")
    public void Initialize(final AndroidViewComponent component, final int cameraType) {
        this.cameraType = cameraType;
        cameraView = (FrameLayout) (view = component.getView());
        removeView(cameraView);
        cameraView.addView(cameraLayout = new AutoFitTextureView(activity));
        cameraLayout.setSurfaceTextureListener(surfaceTextureListener);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);

        cameraLayout.setLayoutParams(params);
        cameraLayout.setOnTouchListener(this);
        Visible(visible);
    }

    private void removeView(final FrameLayout cameraView) {
        if (cameraLayout != null && cameraLayout.getVisibility() == 0) {
            cameraView.removeView(cameraLayout);
        }
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "False")
    @SimpleProperty(description = "Zoom by touch gesture")
    public void PinchToZoom(boolean condition) {
        gestureZoom = condition;
    }

    @SimpleProperty
    public boolean PinchToZoom() {
        return gestureZoom;
    }

    @SimpleEvent(description = "Event fired when custom camera layout is initialized")
    public void Initialized() {
        EventDispatcher.dispatchEvent(this, "Initialized");
    }

    @SimpleEvent(description = "Event fired when custom camera failed to initialize")
    public void InitializeFailed() {
        EventDispatcher.dispatchEvent(this, "InitializeFailed");
    }

    @SimpleEvent(description = "Event fired when saved photo")
    public void SavedPhoto() {
        EventDispatcher.dispatchEvent(this, "SavedPhoto");
    }

    @SimpleEvent(description = "Event fired when refreshed camera")
    public void RefreshedCamera() {
        EventDispatcher.dispatchEvent(this, "RefreshedCamera");
    }

    @SimpleEvent(description = "Event fired when zoom changed")
    public void ZoomChanged() {
        EventDispatcher.dispatchEvent(this, "ZoomChanged");
    }

    @SimpleEvent(description = "Event fired when zoom is being done on the camera layout")
    public void ZoomByPinch(boolean zoomTypeIncrease) {
        EventDispatcher.dispatchEvent(this, "ZoomByGesture", zoomTypeIncrease);
    }

    @SimpleEvent(description = "Event fired when focus locked")
    public void FocusLocked() {
        EventDispatcher.dispatchEvent(this, "FocusLocked");
    }

    @SimpleEvent(description = "Event fired when focus unlocked")
    public void FocusUnlocked() {
        EventDispatcher.dispatchEvent(this, "FocusUnlocked");
    }

    @SimpleEvent(description = "Event fired when camera layout is touched")
    public void Touched() {
        EventDispatcher.dispatchEvent(this, "Touched");
    }

    @SimpleProperty
    public int CameraTypeFront() {
        return 0;
    }

    @SimpleProperty
    public int CameraTypeRear() {
        return 1;
    }

    @SimpleProperty
    public int CurrentCameraType() {
        return cameraType;
    }

    @DesignerProperty(editorType = "boolean", defaultValue = "False")
    @SimpleProperty
    public void Flash(final boolean condition) {
        flashMode = condition;
    }

    @SimpleProperty
    public boolean Flash() {
        return flashMode;
    }

    @DesignerProperty(editorType = "boolean", defaultValue = "False")
    @SimpleProperty
    public void Enhance(final boolean condition) {
        enhance = condition;
    }

    @SimpleProperty
    public boolean Enhance() {
        return enhance;
    }

    @DesignerProperty(editorType = "textArea")
    @SimpleProperty
    public void Output(final String output) {
        imageDestination = output;
    }

    @SimpleProperty
    public String Output() {
        return (imageDestination == null) ? "" : imageDestination;
    }

    @SimpleProperty
    public int DefaultFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_OFF;
    }

    @SimpleProperty
    public int MonoFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_MONO;
    }

    @SimpleProperty
    public int NegativeFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE;
    }

    @SimpleProperty
    public int SepiaFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_SEPIA;
    }

    @SimpleProperty
    public int PosterizeFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_POSTERIZE;
    }

    @SimpleProperty
    public int SolarizeFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_SOLARIZE;
    }

    @SimpleProperty
    public int WhiteboardFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_WHITEBOARD;
    }

    @SimpleProperty
    public int AquaFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_AQUA;
    }

    @SimpleProperty
    public int BlackboardFilter() {
        return CaptureRequest.CONTROL_EFFECT_MODE_BLACKBOARD;
    }

    @SimpleProperty
    public int CameraFilter() {
        return cameraStyle;
    }

    @SimpleProperty(description = "Set noise reduction mode")
    public void NoiseReductionMode(final int mode) {
        noiseReductionMode = mode;
    }

    @SimpleProperty
    public int NoiseReductionMode() {
        return noiseReductionMode;
    }

    @SimpleProperty
    public int NoiseReductionDefault() {
        return CaptureRequest.NOISE_REDUCTION_MODE_OFF;
    }

    @SimpleProperty
    public int NoiseReductionFast() {
        return CaptureRequest.NOISE_REDUCTION_MODE_FAST;
    }

    @SimpleProperty
    public int NoiseReductionHighQuality() {
        return CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY;
    }

    @SimpleProperty
    public int NoiseReductionMinimal() {
        return CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL;
    }

    @SimpleProperty
    public int NoiseReductionNoShutterLag() {
        return CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG;
    }

    @SimpleProperty(description = "Set the camera style type")
    public void CameraFilter(final int filter) {
        cameraStyle = filter;
    }

    @SimpleFunction(description = "Take a picture")
    public void TakePicture() {
        takePicture();
    }

    @SimpleProperty
    public void FaceFocusMode(int mode) {
        faceDetectMode = mode;
    }

    @SimpleProperty
    public int FaceFocusMode() {
        return faceDetectMode;
    }

    @SimpleProperty
    public int FaceFocusModeDefault() {
        return CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
    }

    @SimpleProperty
    public int FaceFocusModeSimple() {
        return CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE;
    }

    @SimpleProperty
    public int FaceFocusModeHigh() {
        return CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
    }

    @SimpleFunction(description = "Take preview photo")
    public void TakePreviewPicture() {
        if(cameraLayout == null) return;
        try (FileOutputStream stream = new FileOutputStream(imageDestination)) {
            cameraLayout.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, stream);
            SavedPhoto();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SimpleFunction(description = "Set zoom, make sure the values are in limit")
    public void Zoom(final float zoomRatio) {
        if (hasZoomSupport && captureRequestBuilder != null && cameraCaptureSessions != null) {
            setZoom(captureRequestBuilder, zoomRatio, false);
        }
    }

    public void setZoom(@NonNull final CaptureRequest.Builder builder, final float zoom, final boolean isCapture) {
        final float newZoom = Math.max(zoom, Math.min(1.0f, maxZoom));
        final int centerX = rectSensor.width() / 2;
        final int centerY = rectSensor.height() / 2;
        final int deltaX = (int)(0.5f * rectSensor.width() / newZoom);
        final int deltaY = (int)(0.5f * rectSensor.height() / newZoom);
        rectCrop.set(centerX - deltaX, centerY - deltaY, centerX + deltaX, centerY + deltaY);
        builder.set(CaptureRequest.SCALER_CROP_REGION, rectCrop);
        try {
            if (!isCapture) {
                cameraCaptureSessions.setRepeatingRequest(builder.build(), null, null);
                ZoomChanged();
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        currentZoom = zoom;
    }

    @SimpleFunction(description = "Locks the camera focus")
    public void LockFocus() {
        if(captureRequestBuilder == null) return;
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, 1);
        try {
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureListener, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SimpleFunction(description = "Check if the camera is active")
    public boolean CameraActive() {
        return cameraLayout == null ? false : cameraLayout.isAvailable() ? true : false;
    }

    @SimpleFunction(description = "Unlocks the camera focus")
    public void UnlockFocus() {
        if(captureRequestBuilder == null) return;
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, 2);
        try {
            cameraCaptureSessions.capture(captureRequestBuilder.build(), captureListener, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SimpleProperty
    public float MaxZoom() {
        zoomSettings();
        return maxZoom;
    }

    @DesignerProperty(defaultValue = "True", editorType = "boolean")
    @SimpleProperty(description = "Set whether camera view should be visible")
    public void Visible(final boolean visible) {
        if (cameraView != null) {
            cameraView.setVisibility(visible ? 0 : 4);
        }
        this.visible = visible;
    }

    @SimpleProperty
    public boolean Visible() {
        return visible;
    }

    @SimpleFunction(description = "Set focus distance")
    public void Focus(final float distance) {
        if(captureRequestBuilder == null) return;
        final CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = cameraManager.getCameraCharacteristics(cameraID);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        final float minimumLens = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        final float num = distance * minimumLens / 100.0f;
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 0);
        captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, num);
        RefreshCamera();
    }

    private void zoomSettings() {
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(cameraID);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        rectSensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        maxZoom = 1.0f;
        hasZoomSupport = false;
        final Float value = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        maxZoom = ((value == null || value < 1.0f) ? 1.0f : value);
        hasZoomSupport = (Float.compare(maxZoom, 1.0f) > 0);
    }

    @SimpleFunction(description = "Refreshes camera")
    public void RefreshCamera() {
        if (cameraLayout == null || cameraDevice == null || captureRequestBuilder == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, (flashMode ? 2 : 0));
        captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, cameraStyle);
        captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode);
        captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);
        updatePreview();
        RefreshedCamera();
    }

    @SimpleFunction(description = "Check if supported on the device")
    public boolean IsSupported() {
        return Build.VERSION.SDK_INT >= 21;
    }

    @SimpleFunction(description = "Reset camera")
    public void ResetCamera() {
        try {
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
        catch (Exception e) {
            Log.e("Custom Camera Error", e.getMessage());
        }
        removeView((FrameLayout) view);
    }

    private void openCamera() {
        final CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);;
        try {
            cameraID = cameraManager.getCameraIdList()[cameraType];
            final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(this.cameraID);
            final StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert cameraManager != null;
            imageDimension = configurationMap.getOutputSizes(SurfaceTexture.class)[0];
            cameraManager.openCamera(cameraID, stateCallback, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(final int viewWidth, final int viewHeight) {
        if (null == cameraLayout || null == imageDimension) {
            return;
        }
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0.0f, 0.0f, (float)viewWidth, (float)viewHeight);
        final RectF bufferRect = new RectF(0.0f, 0.0f, (float)imageDimension.getHeight(), (float)imageDimension.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (1 == rotation || 3 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale = Math.max(viewHeight / (float)imageDimension.getHeight(), viewWidth / (float)imageDimension.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate((float) (90 * (rotation - 2)), centerX, centerY);
        }
        else if (2 == rotation) {
            matrix.postRotate(180.0f, centerX, centerY);
        }
        cameraLayout.setTransform(matrix);
    }

    protected void createCameraPreview() {
        try {
            final SurfaceTexture surfaceTexture = cameraLayout.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            final Surface previewSurface = new Surface(surfaceTexture);
            (captureRequestBuilder = cameraDevice.createCaptureRequest(1)).addTarget(previewSurface);
            if (enhance) {
                captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                captureRequestBuilder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);
                captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
                captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
                captureRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
                captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            }
            captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, (flashMode ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF));
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, cameraStyle);
            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode);
            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
                    if (null == ProCamera.this.cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    Log.e("Camera Zoom", String.valueOf(MaxZoom()));
                    Initialized();
                    updatePreview();
                }

                public void onConfigureFailed(@NonNull final CameraCaptureSession cameraCaptureSession) {
                    InitializeFailed();
                }
            }, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, 1);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void takePicture() {
        if (null == cameraDevice) {
            return;
        }
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(256);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            final ImageReader reader = ImageReader.newInstance(width, height, 256, 1);
            final List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(cameraLayout.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(2);
            captureBuilder.addTarget(reader.getSurface());
            if (enhance) {
                captureBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                captureBuilder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);
                captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
                captureBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
                captureBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
                captureBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            }
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureBuilder.set(CaptureRequest.FLASH_MODE, (flashMode ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF));
            captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, cameraStyle);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode);
            captureBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);

            setZoom(captureBuilder, currentZoom, true);
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(final ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        final byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        writeBytes(bytes);
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener,null);
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                public void onConfigured(final CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, null);
                    }
                    catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                public void onConfigureFailed(final CameraCaptureSession session) {
                    createCameraPreview();
                    TakePreviewPicture();
                }
            }, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void writeBytes(final byte[] data) {
        try (FileOutputStream stream = new FileOutputStream(imageDestination)) {
            stream.write(data);
            SavedPhoto();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event.getPointerCount() > 1 && gestureZoom) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);

            float current_finger_spacing = (float) Math.sqrt(x * x + y * y);
            if(finger_spacing != 0){
                if(current_finger_spacing > finger_spacing && maxZoom > currentZoom){
                    currentZoom = (float) (currentZoom + .1);
                    ZoomByPinch(true);
                } else if (current_finger_spacing < finger_spacing && currentZoom > 1){
                    currentZoom = (float) (currentZoom - .1);
                    ZoomByPinch(false);
                }
                 setZoom(captureRequestBuilder, currentZoom, false);
            }
            finger_spacing = current_finger_spacing;
        } else{
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Touched();
            }
        }
        return true;
    }
}
