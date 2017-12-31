/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf;

import android.view.KeyEvent;
import android.view.MotionEvent;

import org.gearvrf.io.CursorControllerListener;
import org.gearvrf.io.GVRControllerType;
import org.gearvrf.io.GVRInputManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Define a class of type {@link GVRCursorController} to register a new cursor
 * controller with the {@link GVRInputManager}.
 *
 * You can request for all the {@link GVRCursorController}s in the system by
 * querying the {@link GVRInputManager#getCursorControllers()} call.
 *
 * Alternatively all notifications for {@link GVRCursorController}s attached or
 * detached from the framework can be obtained by registering a
 * {@link CursorControllerListener} with the {@link GVRInputManager}.
 *
 * Make use of the {@link GVRCursorController#setCursor(GVRSceneObject)} to
 * add a Cursor for the controller in 3D space. The {@link GVRInputManager} will
 * manipulate the {@link GVRSceneObject} based on the input coming in to the
 * {@link GVRCursorController}.
 *
 * Use the {@link GVRInputManager#addCursorController(GVRCursorController)} call
 * to add an external {@link GVRCursorController} to the framework.
 */
public abstract class GVRCursorController {
    public enum CursorControl
    {
        NONE,
        CURSOR_CONSTANT_DEPTH,
        PROJECT_CURSOR_ON_SURFACE,
        ORIENT_CURSOR_WITH_SURFACE_NORMAL,
        CURSOR_DEPTH_FROM_CONTROLLER
    };
    private static final String TAG = "GVRCursorController";
    private static int uniqueControllerId = 0;
    private final int controllerId;
    private final GVRControllerType controllerType;
    private boolean previousActive;
    private boolean active;
    protected float nearDepth = 0.50f;
    protected float farDepth = 50.0f;
    protected final Vector3f position, origin;
    protected List<KeyEvent> keyEvent;
    private List<KeyEvent> processedKeyEvent;
    protected List<MotionEvent> motionEvent;
    private List<MotionEvent> processedMotionEvent;
    private List<ControllerEventListener> controllerEventListeners;

    protected Object eventLock = new Object();
    protected GVRSceneObject mCursor = null;
    protected boolean enable = false;
    protected boolean mSendEventsToActivity = false;
    protected Object mCursorLock = new Object();
    protected String name;
    protected int vendorId, productId;
    protected GVRScene scene = null;
    protected GVRPicker mPicker = null;
    protected CursorControl mCursorControl = CursorControl.CURSOR_CONSTANT_DEPTH;
    protected float mCursorDepth = 1.0f;
    protected GVRSceneObject mCursorScale;
    protected GVRSceneObject mDragRoot;
    protected GVRSceneObject mDragParent = null;
    protected GVRSceneObject mDragMe = null;
    protected GVRContext context;
    protected volatile boolean mConnected = false;
    protected Vector3f pickDir = new Vector3f(0, 0, -1);

    /**
     * Create an instance of {@link GVRCursorController} only using the
     * {@link GVRControllerType}.
     *
     * Note that this constructor creates a {@link GVRCursorController} with no
     * name (<code>null</code>) and vendor and product id set to 0.
     *
     * @param controllerType the type of this {@link GVRCursorController}.
     */
    public GVRCursorController(GVRContext context, GVRControllerType controllerType) {
        this(context, controllerType, null);
    }

    /**
     * Create an instance of {@link GVRCursorController} using a
     * {@link GVRControllerType} and a name.
     *
     * Note that this constructor creates a {@link GVRCursorController} with  vendor and product
     * id set to 0.
     *
     * @param controllerType the type of this {@link GVRCursorController}.
     * @param name           the name for this {@link GVRCursorController}
     */
    public GVRCursorController(GVRContext context, GVRControllerType controllerType, String name) {
        this(context, controllerType, name, 0, 0);
    }

    /**
     * Create an instance of {@link GVRCursorController}.
     *
     * @param controllerType the type of this {@link GVRCursorController}.
     * @param name           the name for this {@link GVRCursorController}
     * @param vendorId       the vendor id for this {@link GVRCursorController}
     * @param productId      the product id for this {@link GVRCursorController}
     */
    public GVRCursorController(GVRContext context, GVRControllerType controllerType, String name,
                               int vendorId, int productId) {
        this.context = context;
        this.controllerId = uniqueControllerId;
        this.controllerType = controllerType;
        this.name = name;
        this.vendorId = vendorId;
        this.productId = productId;
        uniqueControllerId++;
        position = new Vector3f(0, 0, -1);
        origin = new Vector3f(0, 0, 0);
        keyEvent = new ArrayList<KeyEvent>();
        processedKeyEvent = new ArrayList<KeyEvent>();
        motionEvent = new ArrayList<MotionEvent>();
        processedMotionEvent = new ArrayList<MotionEvent>();
        controllerEventListeners = new CopyOnWriteArrayList<ControllerEventListener>();
        if (mPicker == null)
        {
            mPicker = new GVRPicker(this, false);
        }
        addPickEventListener(mPickHandler);
        addPickEventListener(GVRBaseSensor.getPickHandler());
        mCursorScale = new GVRSceneObject(context);
        mCursorScale.setName("CursorController_CursorScale");
        mDragRoot = new GVRSceneObject(context);
        mDragRoot.setName("CursorController_DragRoot");
        mDragRoot.addChildObject(mCursorScale);
    }

    synchronized public boolean dispatchKeyEvent(KeyEvent event)
    {
        synchronized (eventLock) {
            this.keyEvent.add(event);
        }
        return !mSendEventsToActivity;
    }

    synchronized public boolean dispatchMotionEvent(MotionEvent event)
    {
        synchronized (eventLock) {
            this.motionEvent.add(event);
        }
        return !mSendEventsToActivity;
    }

    /**
     * Return an id that represent this {@link GVRCursorController}
     *
     * @return an integer id that identifies this controller.
     */
    public int getId() {
        return controllerId;
    }

    public GVRContext getGVRContext() { return context; }


    /**
     * Enable or disable routing controller MotionEvents to GVRActivity.
     * <p>
     * When a controller is active, Android MotionEvents are not routed
     * to your application via {@link GVRActivity#dispatchTouchEvent}.
     * Instead they are consumed by the controller.
     * <p>
     * You can listen for {@link IPickEvents} or {@link ITouchEvents}
     * emitted by the{@link GVRPicker} associated with the controller.
     * The {@link GVRPicker.GVRPickedObject} associated with the
     * event may have an Android MotionEvent attached.
     * You can also use a {@link GVRCursorController.ControllerEventListener}
     * to listen for controller events. You can get the motion event
     * with {@link GVRCursorController#getMotionEvent()}.
     * <p>
     * If you enable this option, Android MotionEvent and KeyEvents
     * are routed to your application even though a controller is active.
     * This is useful if you are using Android gesture detection or
     * other Android APIs that rely on getting these events..
     * <p>
     * Do not enable this option if you are using {@link org.gearvrf.scene_objects.GVRViewSceneObject}
     * or {@GVRWidgetPlugin}. These classes route events to the activity for you.
     *
     * @param flag true to send events to GVRActivity, false to not send them
     * @see #sendingEventsToActivity
     * @see GVRCursorController.ControllerEventListener
     * @see #addPickEventListener(IEvents)
     * @see ITouchEvents
     */
    public void sendEventsToActivity(boolean flag)
    {
        mSendEventsToActivity = flag;
    }

    /**
     * Determine whether controller events are being routed to GVRActivity.
     * @return true if events are sent to the activity, else false
     */
    public boolean sendingEventsToActivity() { return mSendEventsToActivity; }

    /**
     * Use this method to set the active state of the{@link GVRCursorController}.
     * It indicates whether or not the "active" button is pressed.
     * <p>
     * It is up to the developer to map the designated button to the active flag.
     * Eg. A Gamepad could attach {@link KeyEvent#KEYCODE_BUTTON_A} to active.
     * @param active    Setting active to true causes the {@link SensorEvent} generated
     *                  from collisions with the cursor to have {@link SensorEvent#isActive()} as
     *                  <code>true</code>. Clearing it will emit events with <code>false</code>.
     *                  The active flag is also propagated to the picker, setting the value of
     *                  {@link GVRPicker.GVRPickedObject#touched}.
     */
    protected void setActive(boolean active) {
        if (isEnabled())
        {
            this.active = active;
        }
    }

    /**
     * Set a {@link GVRSceneObject} to be controlled by the
     * {@link GVRCursorController}.
     *
     * @param object the {@link GVRSceneObject} representing the cursor
     */
    public void setCursor(GVRSceneObject object)
    {
        synchronized (mCursorLock)
        {
            if (object != null)
            {
                if ((mCursor != null) && (mCursor != object))
                {
                    detachCursor();
                }
                attachCursor(object);
                mCursor = object;
                moveCursor();
                object.setEnable(true);
            }
            else if (mCursor != null)
            {
                detachCursor();
            }
            mCursor = object;
        }
    }

    protected void attachCursor(GVRSceneObject cursor)
    {
        GVRSceneObject parent = cursor.getParent();
        if (parent != null)
        {
            parent.removeChildObject(cursor);
        }
        mCursorScale.addChildObject(cursor);
    }

    protected void detachCursor()
    {
        mCursorScale.removeChildObject(mCursor);
    }

    public void setCursorDepth(float depth)
    {
        mCursorDepth = Math.abs(depth);
    }

    public float getCursorDepth()
    {
        return mCursorDepth;
    }

    /**
     * Return the currently set {@link GVRSceneObject}.
     *
     * @return the currently set {@link GVRSceneObject} if there is one, else
     * return <code>null</code>
     */
    public GVRSceneObject getCursor()
    {
        synchronized (mCursorLock)
        {
            return mCursor;
        }
    }

    /**
     * The method will force a process cycle that may result in an
     * {@link ISensorEvents} being generated if there is a significant event
     * that affects a {@link GVRBaseSensor} or {@link IPickEvents} if the
     * cursor pick ray intersects a collider.
     * <p>
     * In most cases when a new position
     * or key event is received, the {@link GVRCursorController} internally
     * invalidates its own data. However there may be situations where the
     * controller data remains the same while the scene graph is changed. This
     * {@link #invalidate()} call can help force the {@link GVRCursorController}
     * to run a new process loop on its existing information against the changed
     * scene graph to generate possible {@link ISensorEvents} for
     * {@link GVRBaseSensor}s.
     */
    public void invalidate() {
        // check if the controller is enabled
        if (isEnabled()) {
            update();
        }
    }

    /**
     * Return the {@link GVRControllerType} associated with the
     * {@link GVRCursorController}.
     *
     * In most cases, this method should return
     * {@link GVRControllerType#EXTERNAL}. {@link GVRControllerType#EXTERNAL}
     * allows the input device to define its own input behavior. If the device
     * wishes to implement {@link GVRControllerType#MOUSE} or
     * {@link GVRControllerType#GAMEPAD} make sure that the behavior is
     * consistent with that defined in GVRMouseDeviceManager and
     * GVRGamepadDeviceManager.
     *
     * @return the {@link GVRControllerType} for the {@link GVRCursorController}
     * .
     */
    public GVRControllerType getControllerType() {
        return controllerType;
    }

    /**
     * Get the picker associated with this controller
     * @return GVRPicker used to pick for this controller
     */
    public GVRPicker getPicker() { return mPicker; }


    /**
     * Establishes how the cursor controller will control the cursor.
     * The cursor control options are specified with
     * {@link GVRCursorController.CursorControl}:
     * <table>
     * <tr><td>CURSOR_CONSTANT_DEPTH</td>
     *     <td>cursor is always kept at constant
     *         distance from origin of pick ray
     *     </td>
     * </tr>
     * <tr<td>PROJECT_CURSOR_ON_SURFACE</td>
     *    <td>cursor depth is changed dynamically to move the
     *        cursor to the hit point on the collider that was hit.
     *    </td>
     * </tr>
     * <tr<td>ORIENT_CURSOR_ON_SURFACE</td>
     *    <td>cursor depth is changed dynamically to move the
     *        cursor to the hit point on the collider that was hit.
     *        In addition the cursor is oriented along the surface normal
     *        in the case of a mesh collider with barycentric coordinate
     *        picking enabled {@link GVRMeshCollider#GVRMeshCollider(GVRContext, GVRMesh, boolean}
     *    </td>
     * </tr>
     * </table>
     * @param control cursor control options
     */
    public void setCursorControl(CursorControl control)
    {
        mCursorControl = control;
    }

    public CursorControl getCursorControl() { return mCursorControl; }


    public boolean startDrag(GVRSceneObject dragMe)
    {
        if (mDragMe != null)
        {
            return false;
        }
        synchronized (mCursorLock)
        {
            GVRTransform objTrans = dragMe.getTransform();
            Matrix4f cursorMtx = mDragRoot.getTransform().getModelMatrix4f();
            Matrix4f objMatrix = objTrans.getModelMatrix4f();

            mDragMe = dragMe;
            mDragParent = dragMe.getParent();
            if (mDragParent != null)
            {
                mDragParent.removeChildObject(dragMe);
            }
            cursorMtx.invert();
            objTrans.setModelMatrix(cursorMtx.mul(objMatrix));
            mDragRoot.addChildObject(dragMe);
        }
        return true;
    }

    public boolean stopDrag()
    {
        if (mDragMe == null)
        {
            return false;
        }
        synchronized (mCursorLock)
        {
            GVRTransform objTrans = mDragMe.getTransform();
            Matrix4f cursorMatrix = mDragRoot.getTransform().getModelMatrix4f();
            mDragRoot.removeChildObject(mDragMe);
            Matrix4f objMatrix = objTrans.getModelMatrix4f();

            objTrans.setModelMatrix(cursorMatrix.mul(objMatrix));
            if (mDragParent != null)
            {
                mDragParent.addChildObject(mDragMe);
            }
            else
            {
                scene.addSceneObject(mDragMe);
            }
            mDragMe = null;
            mDragParent = null;
        }
        return true;
    }

    protected void updateCursor(GVRPicker.GVRPickedObject collision)
    {
        synchronized (mCursorLock)
        {
            GVRTransform cursorTrans = mDragRoot.getTransform();

            if (mCursorControl == CursorControl.NONE)
            {
                return;
            }
            if ((mCursorControl == CursorControl.CURSOR_CONSTANT_DEPTH) ||
                (mCursorControl == CursorControl.CURSOR_DEPTH_FROM_CONTROLLER))
            {
                cursorTrans.setPosition(pickDir.x * mCursorDepth,
                        pickDir.y * mCursorDepth,
                        pickDir.z * mCursorDepth);
                return;
            }
            GVRSceneObject parent = collision.hitObject.getParent();
            float dist = collision.hitDistance;
            float scale = dist / mCursorDepth;

            if (mCursorControl == CursorControl.ORIENT_CURSOR_WITH_SURFACE_NORMAL)
            {
                orientCursor(collision);
            }
            mCursorScale.getTransform().setScale(scale, scale, scale);
            while (parent != null)
            {
                if (parent == mDragRoot)
                {
                    return;
                }
                parent = parent.getParent();
            }
            float xcursor = pickDir.x * dist;   // vector to hit position
            float ycursor = pickDir.y * dist;
            float zcursor = pickDir.z * dist;

            cursorTrans.getTransform().setPosition(xcursor, ycursor, zcursor);
        }
    }

    protected void moveCursor()
    {
        if (mCursorControl != CursorControl.NONE)
        {
            synchronized (mCursorLock)
            {
                GVRTransform trans = mDragRoot.getTransform();
                trans.setRotation(1, 0, 0, 0);
                trans.setPosition(pickDir.x * mCursorDepth, pickDir.y * mCursorDepth, pickDir.z * mCursorDepth);
                mCursorScale.getTransform().setScale(1, 1, 1);
            }
        }
    }

    protected boolean orientCursor(GVRPicker.GVRPickedObject collision)
    {
        GVRSceneObject parent = mCursorScale.getParent();
        float[] baryCoords = collision.getBarycentricCoords();
        boolean coordinatesCalculated = (baryCoords != null) && !Arrays.equals(baryCoords, new float[] {-1f, -1f, -1f});

        if ((parent != null) && coordinatesCalculated)
        {
            float[] normal = collision.getNormalCoords();
            Vector3f lookat = new Vector3f(normal[0], normal[1], normal[2]);
            Vector3f Xaxis = new Vector3f();
            Vector3f Yaxis = new Vector3f();
            Vector3f up = new Vector3f(0, 1, 0);

            up.cross(lookat.x, lookat.y, lookat.z, Xaxis);
            Xaxis = Xaxis.normalize();
            lookat.cross(Xaxis.x, Xaxis.y, Xaxis.z, Yaxis);
            Yaxis = Yaxis.normalize();
            Matrix3f orientMtx = new Matrix3f(Xaxis.x, Xaxis.y, Xaxis.z,
                    Yaxis.x, Yaxis.y, Yaxis.z,
                    lookat.x, lookat.y, lookat.z);
            Quaternionf orient = new Quaternionf();
            orient.setFromNormalized(orientMtx);
            Quaternionf cursorWtL = new Quaternionf();
            Quaternionf hitLtW = new Quaternionf();

            cursorWtL.setFromUnnormalized(parent.getTransform().getModelMatrix4f());
            hitLtW.setFromUnnormalized(collision.hitObject.getTransform().getModelMatrix4f());
            cursorWtL.invert();
            orient.mul(hitLtW);
            orient.mul(cursorWtL);
            orient.normalize();
            GVRTransform cursorTrans = mCursorScale.getTransform();
            cursorTrans.setRotation(orient.w, orient.x, orient.y, orient.z);
            return true;
        }
        return false;
    }


    /**
     * Handler for surface projection of the cursor.
     * Listens for pick events and updates the cursor
     * position on the closest picked object.
     * Also propagates cursor controller events to listeners.
     */
    private IPickEvents mPickHandler = new GVREventListeners.PickEvents()
    {
        public void onPick(GVRPicker picker)
        {
            GVRPicker.GVRPickedObject hit = picker.getPicked()[0];
            if (hit != null)
            {
                updateCursor(hit);
             }
            else
            {
                onNoPick(picker);
            }
        }

        public void onNoPick(GVRPicker picker)
        {
            moveCursor();
       }
    };

    /**
     * Set a key event. Note that this call can be used in lieu of
     * {@link GVRCursorController#setActive(boolean)}.
     *
     * The {@link GVRCursorController} processes a ACTION_DOWN
     * as active <code>true</code> and ACTION_UP as active
     * <code>false</code>.
     *
     * In addition the key event passed is used as a reference for applications
     * that wish to use the contents from the class.
     *
     * {@link #setActive(boolean)} can still be used for applications that do
     * not want to expose key events.
     *
     * @param keyEvent
     */
    protected void setKeyEvent(KeyEvent keyEvent) {
        synchronized (eventLock) {
            this.keyEvent.add(keyEvent);
        }
    }

    /**
     * Get the all the key events processed by the {@link GVRCursorController}
     * if there are any. This call returns all the motion events reports since
     * the last callback was made.
     *
     * Note that not all {@link GVRCursorController} report {@link KeyEvent}s),
     * this function could also return an empty list for
     * {@link GVRCursorController}s that do not generate {@link KeyEvent}s.
     *
     * To get every {@link KeyEvent} reported by the {@link GVRCursorController}
     * use the {@link ControllerEventListener} or the {@link ISensorEvents}
     * listener to query for the {@link KeyEvent} whenever a a callback is made.
     *
     * The {@link KeyEvent} would be valid for the lifetime of that callback and
     * would be reset to null on completion.
     *
     * @return the list of {@link KeyEvent}s processed by the
     * {@link GVRCursorController}.
     */
    public List<KeyEvent> getKeyEvents() {
        synchronized (eventLock) {
            return processedKeyEvent;
        }
    }

    /**
     * Get the latest key event processed by the {@link GVRCursorController} if
     * there is one (not all {@link GVRCursorController} report {@link KeyEvent}
     * s). Note that this value will be null if the latest event processed by
     * the {@link GVRCursorController} did not contain a {@link KeyEvent}.
     *
     * Note that this function also returns a null. To get every
     * {@link KeyEvent} reported by the {@link GVRCursorController} use the
     * {@link ControllerEventListener} or the {@link ISensorEvents} listener to
     * query for the {@link KeyEvent} whenever a a callback is made.
     *
     * The {@link KeyEvent} would be valid for the lifetime of that callback and
     * would be reset to null on completion.
     *
     * @return the {@link KeyEvent} or null if there isn't one.
     */
    public KeyEvent getKeyEvent() {
        synchronized (eventLock) {
            if (processedKeyEvent.isEmpty()) {
                return null;
            } else {
                return processedKeyEvent.get(processedKeyEvent.size() - 1);
            }
        }
    }

    /**
     * Set the latest motion event processed by the {@link GVRCursorController}.
     *
     * Make sure not to recycle the passed {@link MotionEvent}. The
     * {@link GVRCursorController} will recycle the {@link MotionEvent} after
     * completion.
     *
     * @param motionEvent the {@link MotionEvent} processed by the
     *                    {@link GVRCursorController}.
     */
    protected void setMotionEvent(MotionEvent motionEvent) {
        synchronized (eventLock) {
            this.motionEvent.add(motionEvent);
        }
    }

    /**
     * Get the all the {@link MotionEvent}s processed by the
     * {@link GVRCursorController} if there are any. This call returns all the
     * motion events reports since the last callback was made.
     *
     * Note that not all {@link GVRCursorController}s report {@link MotionEvent}s,
     * this function also returns an empty list. To get every
     * {@link MotionEvent} reported by the {@link GVRCursorController} use the
     * {@link ControllerEventListener} or the {@link ISensorEvents} listener to
     * query for the {@link MotionEvent}s whenever a a callback is made.
     *
     * The {@link MotionEvent}s reported would be valid for the lifetime of that
     * callback and would be recycled and reset on completion. Make use to the
     * {@link MotionEvent#obtain(MotionEvent)} to clone a copy of the
     * {@link MotionEvent}.
     *
     * @return a list of {@link MotionEvent}s processed by the
     * {@link GVRCursorController} .
     */
    public List<MotionEvent> getMotionEvents() {
        synchronized (eventLock) {
            return processedMotionEvent;
        }
    }

    /**
     * Get the latest {@link MotionEvent} processed by the
     * {@link GVRCursorController} if there is one (not all
     * {@link GVRCursorController}s report {@link MotionEvent}s)
     *
     * Note that this function also returns a null. To get every
     * {@link MotionEvent} reported by the {@link GVRCursorController} use the
     * {@link ControllerEventListener} or the {@link ISensorEvents} listener to
     * query for the {@link MotionEvent} whenever a a callback is made.
     *
     * The {@link MotionEvent} would be valid for the lifetime of that callback
     * and would be recycled and reset to null on completion. Make use to the
     * {@link MotionEvent#obtain(MotionEvent)} to clone a copy of the
     * {@link MotionEvent}.
     *
     * @return the latest {@link MotionEvent} processed by the
     * {@link GVRCursorController} or null.
     */
    public MotionEvent getMotionEvent() {
        synchronized (eventLock) {
            if (processedMotionEvent.isEmpty()) {
                return null;
            } else {
                return processedMotionEvent
                        .get(processedMotionEvent.size() - 1);
            }
        }
    }

    /**
     * This call sets the position of the {@link GVRCursorController}.
     *
     * Use this call to also set an initial position for the Cursor when a new
     * {@link GVRCursorController} is returned by the
     * {@link CursorControllerListener}.
     *
     * @param x the x value of the position.
     * @param y the y value of the position.
     * @param z the z value of the position.
     */
    public void setPosition(float x, float y, float z)
    {
        if (isEnabled())
        {
            position.set(x, y, z);
            update();
        }
    }


    public Vector3f getPosition(Vector3f pos) {
        pos.set(position);
        return pos;
    }

    /**
     * Register a {@link ControllerEventListener} to receive a callback whenever
     * the {@link GVRCursorController} has been updated.
     *
     * Use the {@link GVRCursorController} methods to query for information
     * about the {@link GVRCursorController}.
     */
    public interface ControllerEventListener {
        void onEvent(GVRCursorController controller, boolean isActive);
    }

    /**
     * Add a {@link ControllerEventListener} to receive updates from this
     * {@link GVRCursorController}.
     *
     * @param listener the {@link CursorControllerListener} to be added.
     */
    public void addControllerEventListener(ControllerEventListener listener) {
        controllerEventListeners.add(listener);
    }

    /**
     * Remove the previously added {@link ControllerEventListener}.
     *
     * @param listener {@link ControllerEventListener} that was previously added .
     */
    public void removeControllerEventListener(ControllerEventListener listener) {
        controllerEventListeners.remove(listener);
    }

    /**
     * Add a {@link IPickEvents} or {@link ITouchEvents} listener to receive updates from this
     * {@link GVRCursorController}. A pick event is emitted whenever
     * the pick ray from the controller intersects a {@link GVRCollider}.
     * A touch event is emitted when the active button is pressed while
     * the pick ray is inside the collider.
     *
     * @param listener the {@link IPickEvents} or {@link ITouchEvents} listener to be added.
     */
    public void addPickEventListener(IEvents listener)
    {
        if (IPickEvents.class.isAssignableFrom(listener.getClass()) ||
            ITouchEvents.class.isAssignableFrom(listener.getClass()))
        {
            mPicker.getEventReceiver().addListener(listener);
        }
        else
        {
            throw new IllegalArgumentException("Pick event listener must be derive from IPickEvents or ITouchEvents");
        }
    }

    /**
     * Remove the previously added pick or touch listener.
     *
     * @param listener {@link IPickEvents} or {@link ITouchEvents} listener that was previously added.
     */
    public void removePickEventListener(IEvents listener)
    {
        mPicker.getEventReceiver().removeListener(listener);
    }

    /**
     * Use this method to enable or disable the {@link GVRCursorController}.
     *
     * By default the {@link GVRCursorController} is enabled. If disabled, the
     * controller would not report new positions for the cursor and would not
     * generate {@link SensorEvent}s to {@link GVRBaseSensor}s.
     *
     * @param flag <code>true</code> to enable the {@link GVRCursorController},
     *               <code>false</code> to disable.
     */
    public void setEnable(boolean flag) {
        mPicker.setEnable(flag);
        mDragRoot.setEnable(flag);
        if (this.enable == flag)
        {
            // nothing to be done here, return
            return;
        }
        this.enable = flag;
        if (!flag)
        {
            // reset
            position.zero();
            if (previousActive) {
                active = false;
            }

            synchronized (eventLock) {
                keyEvent.clear();
                motionEvent.clear();
            }
            update();
            context.getInputManager().removeCursorController(this);
        }
    }

    /**
     * Check if the {@link GVRCursorController} is enabled or disabled.
     *
     * By default the {@link GVRCursorController} is enabled.
     *
     * @return <code>true</code> if the {@link GVRCursorController} is enabled,
     * <code>false</code> otherwise.
     */
    public boolean isEnabled() {
        return enable;
    }

    /**
     * Check if the {@link GVRCursorController} is mConnected and providing
     * input data.
     * @return true if controller is mConnected, else false
     */
    public boolean isConnected() { return mConnected; }

    /**
     * Set the near depth value for the controller. This is the closest the
     * {@link GVRCursorController} can get in relation to the {@link GVRCamera}.
     *
     * By default this value is set to zero.
     *
     * @param nearDepth
     */
    public void setNearDepth(float nearDepth) {
        this.nearDepth = nearDepth;
    }

    /**
     * Set the far depth value for the controller. This is the farthest the
     * {@link GVRCursorController} can get in relation to the {@link GVRCamera}.
     *
     * By default this value is set to negative {@link Float#MAX_VALUE}.
     *
     * @param farDepth
     */
    public void setFarDepth(float farDepth) {
        this.farDepth = farDepth;
    }

    /**
     * Get the near depth value for the controller.
     *
     * @return value representing the near depth. By default the value returned
     * is zero.
     */
    public float getNearDepth() {
        return nearDepth;
    }

    /**
     * Get the far depth value for the controller.
     *
     * @return value representing the far depth. By default the value returned
     * is negative {@link Float#MAX_VALUE}.
     */
    public float getFarDepth() {
        return farDepth;
    }

    /**
     * Get the product id associated with the {@link GVRCursorController}
     *
     * @return an integer representing the product id if there is one, else
     * return zero.
     */
    public int getProductId() {
        return productId;
    }

    /**
     * Get the vendor id associated with the {@link GVRCursorController}
     *
     * @return an integer representing the vendor id if there is one, else
     * return zero.
     */
    public int getVendorId() {
        return vendorId;
    }

    /**
     * Get the name associated with the {@link GVRCursorController}.
     *
     * @return a string representing the {@link GVRCursorController} is there is
     * one, else return <code>null</code>
     */
    public String getName() {
        return name;
    }

    /**
     * Change the scene associated with this controller (and
     * its associated picker). The picker is not enabled
     * and will not automatically pick. The controller
     * explicity calls GVRPicker.processPick each tima a
     * controller event is received.
     * @param scene The scene from which to pick colliders
     */
    public void setScene(GVRScene scene)
    {
        mPicker.setScene(scene);
        if (scene != null)
        {
            synchronized (mCursorLock)
            {
                if (mDragRoot.getParent() != null)
                {
                    mDragRoot.getParent().removeChildObject(mDragRoot);
                }
                scene.getMainCameraRig().addChildObject(mDragRoot);
            }
        }
        this.scene = scene;
    }

    /**
     * Update the state of the picker. If it has an owner, the picker
     * will use that object to derive its position and orientations.
     * The "active" state of this controller is used to indicate touch.
     */
    protected void updatePicker(MotionEvent event)
    {
        float l = position.length();
        boolean isActive = active;

        if (!mPicker.isEnabled() && (l > 0.00001f))
        {
            mPicker.setPickRay(0, 0, 0, pickDir.x, pickDir.y, pickDir.z);
        }
        mPicker.processPick(isActive, event); // move cursor if there is a pick
    }

    private boolean eventHandledBySensor = false;

    /**
     * Returns whether events generated as a result of the latest change in the
     * GVRCursorController state, i.e. change in Position, or change in Active/Enable state were
     * handled by a sensor. This can be used by an application to know whether
     * there were any {@link SensorEvent}s generated as a result of any change in the
     * {@link GVRCursorController}.
     * @return <code>true</code> if event was handled by a sensor,
     * <code>false</code> if otherwise.
     */
    public boolean isEventHandledBySensorManager() {
        return eventHandledBySensor;
    }

    void setEventHandledBySensor()
    {
        eventHandledBySensor = true;
    }

    /**
     * Process the input data.
     */
    private void update()
    {
        // set the newly received key and motion events.
        synchronized (eventLock)
        {
            processedKeyEvent.addAll(keyEvent);
            keyEvent.clear();
            processedMotionEvent.addAll(motionEvent);
            motionEvent.clear();
        }

        previousActive = active;
        eventHandledBySensor = false;
        if (scene != null)
        {
            updatePicker(getMotionEvent());
        }
        for (ControllerEventListener listener : controllerEventListeners)
        {
            listener.onEvent(this, active);
        }
        // reset the set key and motion events.
        synchronized (eventLock)
        {
            processedKeyEvent.clear();
            // done processing, recycle
            for (MotionEvent event : processedMotionEvent)
            {
                event.recycle();
            }
            processedMotionEvent.clear();
        }
    }

    /**
     * Sets the x, y, z location from where the pick begins
     * Should match the location of the camera or the hand controller.
     * @param x X position of the camera
     * @param y Y position of the camera
     * @param z Z position of the camera
     */
    public void setOrigin(float x, float y, float z){
        origin.set(x,y,z);
    }


    /**
     * Returns the origin of the pick ray for this controller.
     * @return X,Y,Z origin of picking ray
     */
    public Vector3f getOrigin()
    {
        return origin;
    }
}