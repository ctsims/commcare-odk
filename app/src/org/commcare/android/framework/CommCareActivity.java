package org.commcare.android.framework;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.dialogs.DialogController;
import org.commcare.util.SessionFrame;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.AudioController;
import org.odk.collect.android.views.media.MediaState;
import org.odk.collect.android.views.media.MediaEntity;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Base class for CommCareActivities to simplify 
 * common localization and workflow tasks
 * 
 * @author ctsims
 *
 */
public abstract class CommCareActivity<R> extends FragmentActivity implements CommCareTaskConnector<R>, 
    AudioController, DialogController {
    
    protected final static int DIALOG_PROGRESS = 32;
    protected final static String DIALOG_TEXT = "cca_dialog_text";
    public final static String KEY_DIALOG_FRAG = "dialog_fragment";

    StateFragment stateHolder;
    private boolean firstRun = true;
    
    //Fields for implementation of AudioController
    private MediaEntity currentEntity;
    private AudioButton currentButton;
    private MediaState stateBeforePause;
    
    //fields for implementing task transitions for CommCareTaskConnector
    boolean inTaskTransition;
    boolean shouldDismissDialog = true;
    
    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @Override
    @TargetApi(14)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentManager fm = this.getSupportFragmentManager();
        
        stateHolder = (StateFragment) fm.findFragmentByTag("state");
        
        // If the state holder is null, create a new one for this activity
        if (stateHolder == null) {
            stateHolder = new StateFragment();
            fm.beginTransaction().add(stateHolder, "state").commit();
        } else {
            if(stateHolder.getPreviousState() != null){
                firstRun = stateHolder.getPreviousState().isFirstRun();
                loadPreviousAudio(stateHolder.getPreviousState());
            } else{
                firstRun = true;
            }
        }
        
        if(this.getClass().isAnnotationPresent(ManagedUi.class)) {
            this.setContentView(this.getClass().getAnnotation(ManagedUi.class).value());
            loadFields();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayShowCustomEnabled(true);

            //Add breadcrumb bar
            
            BreadcrumbBarFragment bar = (BreadcrumbBarFragment) fm.findFragmentByTag("breadcrumbs");
            
            // If the state holder is null, create a new one for this activity
            if (bar == null) {
                bar = new BreadcrumbBarFragment();
                fm.beginTransaction().add(bar, "breadcrumbs").commit();
            }
        }
    }
    
    private void loadPreviousAudio(AudioController oldController) {
        MediaEntity oldEntity = oldController.getCurrMedia();
        if (oldEntity != null) {
            this.currentEntity = oldEntity;
            oldController.removeCurrentMediaEntity();
        }
    }
    
    private void playPreviousAudio() {
        if (currentEntity == null) return;
        switch (currentEntity.getState()) {
        case PausedForRenewal:
            playCurrentMediaEntity();
            break;
        case Paused:
            break;
        case Playing:
        case Ready:
            System.out.println("WARNING: state in loadPreviousAudio is invalid");
        }
    }
    
    /*
     * Method to override in classes that need some functions called only once at the start 
     * of the life cycle. Called by the CommCareActivity onResume() method; so, after the onCreate()
     * method of all classes, but before the onResume() of the overriding activity. State maintained in
     * stateFragment Fragment and firstRun boolean. 
     */
    public void fireOnceOnStart(){
        // override when needed
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                try { 
                    CommCareApplication._().getCurrentSession().clearAllState();
                } catch(SessionUnavailableException sue) {
                    // probably won't go anywhere with this
                }
                // app icon in action bar clicked; go home
                Intent intent = new Intent(this, CommCareHomeActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void loadFields() {
        CommCareActivity oldActivity = stateHolder.getPreviousState();
        Class c = this.getClass();
        for(Field f : c.getDeclaredFields()) {
            if(f.isAnnotationPresent(UiElement.class)) {
                UiElement element = f.getAnnotation(UiElement.class);
                try{
                    f.setAccessible(true);
                    
                    try {
                        View v = this.findViewById(element.value());
                        f.set(this, v);
                        
                        if(oldActivity != null) {
                            View oldView = (View)f.get(oldActivity);
                            if(oldView != null) {
                                if(v instanceof TextView) {
                                    ((TextView)v).setText(((TextView)oldView).getText());
                                }
                                v.setVisibility(oldView.getVisibility());
                                v.setEnabled(oldView.isEnabled());
                                continue;
                            }
                        }
                        
                        if(element.locale() != "") {
                            if(v instanceof TextView) {
                                ((TextView)v).setText(Localization.get(element.locale()));
                            } else {
                                throw new RuntimeException("Can't set the text for a " + v.getClass().getName() + " View!");
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        throw new RuntimeException("Bad Object type for field " + f.getName());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Couldn't access the activity field for some reason");
                    }
                } finally {
                    f.setAccessible(false);
                }
            }
        }
    }
    
    protected CommCareActivity getDestroyedActivityState() {
        return stateHolder.getPreviousState();
    }
    
    protected boolean isTopNavEnabled() {
        return false;
    }
    
    boolean visible = false;
    

    /* (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    @TargetApi(11)
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            //If we're in honeycomb this is taken care of by the fragment
        } else {
            this.setTitle(getTitle(this, getActivityTitle()));
        }
        visible = true;
        playPreviousAudio();
        //set that this activity has run
        if(isFirstRun()){
            fireOnceOnStart();
            setActivityHasRun();
        }
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        if (currentEntity != null) saveEntityStateAndClear();
    }
    
    protected boolean isInVisibleState() {
        return visible;
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentEntity != null) attemptSetStateToPauseForRenewal();
    }

    /* (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#connectTask(org.commcare.android.tasks.templates.CommCareTask)
     */
    @Override
    public <A, B, C> void connectTask(CommCareTask<A, B, C, R> task) {
        //If stateHolder is null here, it's because it is restoring itself, it doesn't need
        //this step
        wakelock();
        stateHolder.connectTask(task);
        
        //If we've left an old dialog showing during the task transition and it was from the same task
        //as the one that is starting, don't dismiss it
        CustomProgressDialog currDialog = getCurrentDialog();
        if (currDialog != null && currDialog.getTaskId() == task.getTaskId()) {
            shouldDismissDialog = false;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#getReceiver()
     */
    @Override
    public R getReceiver() {
        return (R)this;
    }
    
    /* (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#startBlockingForTask()
     * 
     * Override these to control the UI for your task
     */
    @Override
    public void startBlockingForTask(int id) {        
        //attempt to dismiss the dialog from the last task before showing this one
        attemptDismissDialog();
        
        //ONLY if shouldDismissDialog = true, i.e. if we chose to dismiss the last dialog during transition, show a new one
        if (id >= 0 && shouldDismissDialog) {
            this.showProgressDialog(id);
        }
    }

    /* (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#stopBlockingForTask()
     */
    @Override
    public void stopBlockingForTask(int id) {
        if (id >= 0) { 
            if (inTaskTransition) {
                shouldDismissDialog = true;
            }
            else {
                dismissProgressDialog();
            }
        }
        unlock();
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#startTaskTransition()
     */
    @Override
    public void startTaskTransition() {
        inTaskTransition = true;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#stopTaskTransition()
     */
    @Override
    public void stopTaskTransition() {
        inTaskTransition = false;
        attemptDismissDialog();
        //reset shouldDismissDialog to true after this transition cycle is over
        shouldDismissDialog = true;
    }
    
    //if shouldDismiss flag has not been set to false in the course of a task transition,
    //then dismiss the dialog
    public void attemptDismissDialog() {
        if (shouldDismissDialog) {
            dismissProgressDialog();
        }
    }
    
    /**
     * Handle an error in task execution.  
     * 
     * @param e
     */
    protected void taskError(Exception e) {
        //TODO: For forms with good error reporting, integrate that
        Toast.makeText(this, Localization.get("activity.task.error.generic", new String[] {e.getMessage()}), Toast.LENGTH_LONG).show();
        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, e.getMessage());
    }
    
    /**
     * Display exception details as a pop-up to the user.
     *
     * @param e Exception to handle
     */
    protected void displayException(Exception e) {
        String mErrorMessage = e.getMessage();
        AlertDialog mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setTitle(Localization.get("notification.case.predicate.title"));
        mAlertDialog.setMessage(Localization.get("notification.case.predicate.action", new String[] {mErrorMessage}));
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            /*
             * (non-Javadoc)
             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
             */
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        finish();
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(Localization.get("dialog.ok"), errorListener);
        mAlertDialog.show();
    }

    /* (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
     */
    @Override
    public void taskCancelled(int id) {
        
    }
    
    /**
     * 
     */
    public void cancelCurrentTask() {
        stateHolder.cancelTask();
    }
    
    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onStop()
     */
    @Override
    public void onStop() {
        super.onStop();
    }
        
    private void wakelock() {
        int lockLevel = getWakeLockingLevel();
        if(lockLevel == -1) { return;}
        
        stateHolder.wakelock(lockLevel);
    }
    
    private void unlock() {
        stateHolder.unlock();
    }
    
    /**
     * @return The WakeLock flags that should be used for this activity's tasks. -1
     * if this activity should not acquire/use the wakelock for tasks
     */
    protected int getWakeLockingLevel() {
        return -1;
    }
    
    //Graphical stuff below, needs to get modularized
    
    public void TransplantStyle(TextView target, int resource) {
        //get styles from here
        TextView tv = (TextView)View.inflate(this, resource, null);
        int[] padding = {target.getPaddingLeft(), target.getPaddingTop(), target.getPaddingRight(),target.getPaddingBottom() };

        target.setTextColor(tv.getTextColors().getDefaultColor());
        target.setTypeface(tv.getTypeface());
        target.setBackgroundDrawable(tv.getBackground());
        target.setPadding(padding[0], padding[1], padding[2], padding[3]);
    }
    
    /**
     * The right-hand side of the title associated with this activity.
     * 
     * This will update dynamically as the activity loads/updates, but if
     * it will ever have a value it must return a blank string when one
     * isn't available.
     * 
     * @return
     */
    public String getActivityTitle() {
        return null;
    }
    
    public static String getTopLevelTitleName(Context c) {
        String topLevel = null;
        try {
            topLevel = Localization.get("app.display.name");
            return topLevel;
        } catch(NoLocalizedTextException nlte) {
            //nothing, app display name is optional for now.
        }
        
        return c.getString(org.commcare.dalvik.R.string.title_bar_name);
    }
    
    public static String getTitle(Context c, String local) {
        String topLevel = getTopLevelTitleName(c);
        
        String[] stepTitles = new String[0];
        try {
            stepTitles = CommCareApplication._().getCurrentSession().getHeaderTitles();
            
            //See if we can insert any case hacks
            int i = 0;
            for(String[] step : CommCareApplication._().getCurrentSession().getFrame().getSteps()){
                try {
                if(SessionFrame.STATE_DATUM_VAL.equals(step[0])) {
                    //Haaack
                    if(step[1] != null && step[1].contains("case_id")) {
                        ACase foundCase = CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class).getRecordForValue(ACase.INDEX_CASE_ID, step[2]);
                        stepTitles[i] = Localization.get("title.datum.wrapper", new String[] { foundCase.getName()});
                    }
                }
                } catch(Exception e) {
                    //TODO: Your error handling is bad and you should feel bad
                }
                ++i;
            }
            
        } catch(SessionUnavailableException sue) {
            
        }
        
        String returnValue = topLevel;
        
        for(String title : stepTitles) {
            if(title != null) {
                returnValue += " > " + title;
            }
        }
        
        if(local != null) {
            returnValue += " > " + local;
        }
        return returnValue;
    }
    
    public void setActivityHasRun(){
        this.firstRun = false;
    }
    
    public boolean isFirstRun(){
        return this.firstRun;
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#getCurrMedia()
     * 
     * All methods for implementation of AudioController
     */
    @Override
    public MediaEntity getCurrMedia() {
        return currentEntity;
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#refreshCurrentAudioButton(org.odk.collect.android.views.media.AudioButton)
     */
    @Override
    public void refreshCurrentAudioButton(AudioButton clicked) {
        if (currentButton != null && currentButton != clicked) {
            currentButton.setStateToReady();
        }
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#setCurrent(org.odk.collect.android.views.media.MediaEntity, org.odk.collect.android.views.media.AudioButton)
     */
    @Override
    public void setCurrent(MediaEntity e, AudioButton b) {
        refreshCurrentAudioButton(b);
        setCurrent(e);
        setCurrentAudioButton(b);
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#setCurrent(org.odk.collect.android.views.media.MediaEntity)
     */
    @Override
    public void setCurrent(MediaEntity e) {
        releaseCurrentMediaEntity();
        currentEntity = e;
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#setCurrentAudioButton(org.odk.collect.android.views.media.AudioButton)
     */
    @Override
    public void setCurrentAudioButton(AudioButton b) {
        currentButton = b;
    }
    
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#releaseCurrentMediaEntity()
     */
    @Override
    public void releaseCurrentMediaEntity() {
        if (currentEntity != null) {
            MediaPlayer mp = currentEntity.getPlayer();
            mp.reset();
            mp.release();    
        }
        currentEntity = null;
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#playCurrentMediaEntity()
     */
    @Override
    public void playCurrentMediaEntity() {
        if (currentEntity != null) {
            MediaPlayer mp = currentEntity.getPlayer();
            mp.start();            
            currentEntity.setState(MediaState.Playing);
        }
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#pauseCurrentMediaEntity()
     */
    @Override
    public void pauseCurrentMediaEntity() {
        if (currentEntity != null && currentEntity.getState().equals(MediaState.Playing)) {
            MediaPlayer mp = currentEntity.getPlayer();
            mp.pause();    
            currentEntity.setState(MediaState.Paused);
        }
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#getMediaEntityId()
     */
    @Override
    public Object getMediaEntityId() {
        return currentEntity.getId();
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#attemptSetStateToPauseForRenewal()
     */
    @Override
    public void attemptSetStateToPauseForRenewal() {
        if (stateBeforePause != null && stateBeforePause.equals(MediaState.Playing)) {
            currentEntity.setState(MediaState.PausedForRenewal);
        }
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#saveEntityStateAndClear()
     */
    @Override
    public void saveEntityStateAndClear() {
        stateBeforePause = currentEntity.getState();
        pauseCurrentMediaEntity();
        refreshCurrentAudioButton(null);
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#setMediaEntityState(org.odk.collect.android.views.media.MediaState)
     */
    @Override
    public void setMediaEntityState(MediaState state) {
        currentEntity.setState(state);
    }
    
    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.views.media.AudioController#removeCurrentMediaEntity()
     */
    @Override
    public void removeCurrentMediaEntity() {
        currentEntity = null;
    }
    
    /** All methods for implementation of DialogController **/


    /*
     * (non-Javadoc)
     * @see org.commcare.dalvik.dialogs.DialogController#updateProgress(java.lang.String, int)
     */
    @Override
    public void updateProgress(String updateText, int taskId) {
        CustomProgressDialog mProgressDialog = getCurrentDialog();
        if (mProgressDialog != null) {
            if (mProgressDialog.getTaskId() == taskId) {
                mProgressDialog.updateMessage(updateText);
            }
            else {
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, 
                        "Attempting to update a progress dialog whose taskId does not match the"
                        + "task for which the update message was intended.");
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.dalvik.dialogs.DialogController#showProgressDialog(int)
     */
    @Override
    public void showProgressDialog(int taskId) {
        CustomProgressDialog dialog = generateProgressDialog(taskId);
        if (dialog != null) {
            dialog.show(getSupportFragmentManager(), KEY_DIALOG_FRAG);
        }
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.dalvik.dialogs.DialogController#getCurrentDialog()
     */
    @Override
    public CustomProgressDialog getCurrentDialog() {
        return (CustomProgressDialog) getSupportFragmentManager().
                findFragmentByTag(KEY_DIALOG_FRAG);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.dalvik.dialogs.DialogController#dismissProgressDialog()
     */
    @Override
    public void dismissProgressDialog() {
        CustomProgressDialog mProgressDialog = getCurrentDialog();
        if (mProgressDialog != null && mProgressDialog.isAdded()) {
            mProgressDialog.dismissAllowingStateLoss();
        }
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.dalvik.dialogs.DialogController#generateProgressDialog(int)
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        //dummy method for compilation, implementation handled in those subclasses that need it
        return null;
    }
    
}
