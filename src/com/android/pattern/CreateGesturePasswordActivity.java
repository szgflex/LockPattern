package com.android.pattern;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.android.lockpattern.R;
import com.android.lockpattern.widget.LockPatternSmallView;
import com.android.lockpattern.widget.LockPatternUtils;
import com.android.lockpattern.widget.LockPatternView;
import com.android.lockpattern.widget.LockPatternView.Cell;
import com.android.lockpattern.widget.LockPatternView.DisplayMode;

public class CreateGesturePasswordActivity extends Activity {
	static final String TAG = "CreateGesturePassword";
	
	private static final int ID_EMPTY_MESSAGE = -1;
	private static final String KEY_UI_STAGE = "uiStage";
	private static final String KEY_PATTERN_CHOICE = "chosenPattern";
	
	private LockPatternView mLockPatternView;
	protected TextView mHeaderText;
	
	protected List<LockPatternView.Cell> mChosenPattern = null;
	private Toast mToast;
	private Stage mUiStage = Stage.Introduction;
	
	private LockPatternSmallView mPreviewViews;
	/**
	 * The patten used during the help screen to show how to draw a pattern.
	 */
	private final List<LockPatternView.Cell> mAnimatePattern = new ArrayList<LockPatternView.Cell>();

	/**
	 * The states of the left footer button.
	 */
	enum LeftButtonMode {
		Cancel(android.R.string.cancel, true), 
		CancelDisabled(android.R.string.cancel, false), 
		Retry(R.string.lockpattern_retry_button_text, true), 
		RetryDisabled(R.string.lockpattern_retry_button_text, false), 
		Gone(ID_EMPTY_MESSAGE, false);

		/**
		 * @param text
		 *            The displayed text for this mode.
		 * @param enabled
		 *            Whether the button should be enabled.
		 */
		LeftButtonMode(int text, boolean enabled) {
			this.text = text;
			this.enabled = enabled;
		}

		final int text;
		final boolean enabled;
	}

	/**
	 * The states of the right button.
	 */
	enum RightButtonMode {
		Continue(R.string.lockpattern_continue_button_text, true), 
		
		ContinueDisabled(R.string.lockpattern_continue_button_text, false), 
		
		Confirm(R.string.lockpattern_confirm_button_text, true), 
		
		ConfirmDisabled(R.string.lockpattern_confirm_button_text, false), 
		
		Ok(android.R.string.ok, true);

		/**
		 * @param text
		 *            The displayed text for this mode.
		 * @param enabled
		 *            Whether the button should be enabled.
		 */
		RightButtonMode(int text, boolean enabled) {
			this.text = text;
			this.enabled = enabled;
		}

		final int text;
		final boolean enabled;
	}

	/**
	 * Keep track internally of where the user is in choosing a pattern.
	 */
	protected enum Stage {

		Introduction(
				R.string.lockpattern_recording_intro_header,
				LeftButtonMode.Cancel, 
				RightButtonMode.ContinueDisabled,
				ID_EMPTY_MESSAGE, 
				true), 
				
		HelpScreen(
				R.string.lockpattern_settings_help_how_to_record,
				LeftButtonMode.Gone, 
				RightButtonMode.Ok, 
				ID_EMPTY_MESSAGE,
				false), 
				
		ChoiceTooShort(
				R.string.lockpattern_recording_incorrect_too_short,
				LeftButtonMode.Retry, 
				RightButtonMode.ContinueDisabled,
				ID_EMPTY_MESSAGE, 
				true), 
				
		FirstChoiceValid(
				R.string.lockpattern_pattern_entered_header,
				LeftButtonMode.Retry, 
				RightButtonMode.Continue,
				ID_EMPTY_MESSAGE, 
				false),
				
		NeedToConfirm(
				R.string.lockpattern_need_to_confirm, 
				LeftButtonMode.Cancel,
				RightButtonMode.ConfirmDisabled, 
				ID_EMPTY_MESSAGE, 
				true),
				
		ConfirmWrong(
				R.string.lockpattern_need_to_unlock_wrong,
				LeftButtonMode.Cancel, 
				RightButtonMode.ConfirmDisabled,
				ID_EMPTY_MESSAGE, 
				true),
			
		ChoiceConfirmed(
				R.string.lockpattern_pattern_confirmed_header,
				LeftButtonMode.Cancel, 
				RightButtonMode.Confirm,
				ID_EMPTY_MESSAGE, 
				false);

		/**
		 * @param headerMessage
		 *            The message displayed at the top.
		 * @param leftMode
		 *            The mode of the left button.
		 * @param rightMode
		 *            The mode of the right button.
		 * @param footerMessage
		 *            The footer message.
		 * @param patternEnabled
		 *            Whether the pattern widget is enabled.
		 */
		Stage(int headerMessage, LeftButtonMode leftMode,
				RightButtonMode rightMode, int footerMessage,
				boolean patternEnabled) {
			this.headerMessage = headerMessage;
			this.leftMode = leftMode;
			this.rightMode = rightMode;
			this.footerMessage = footerMessage;
			this.patternEnabled = patternEnabled;
		}

		final int headerMessage;
		final LeftButtonMode leftMode;
		final RightButtonMode rightMode;
		final int footerMessage;
		final boolean patternEnabled;
	}

	private void showToast(CharSequence message) {
		if (null == mToast) {
			mToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		} else {
			mToast.setText(message);
		}

		mToast.show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gesturepassword_create);
		
		// 初始化演示动画
		mAnimatePattern.add(LockPatternView.Cell.of(0, 0));
		mAnimatePattern.add(LockPatternView.Cell.of(0, 1));
		mAnimatePattern.add(LockPatternView.Cell.of(1, 1));
		mAnimatePattern.add(LockPatternView.Cell.of(2, 1));
		mAnimatePattern.add(LockPatternView.Cell.of(2, 2));

		mLockPatternView = (LockPatternView) findViewById(R.id.gesturepwd_create_lockview);
		mHeaderText = (TextView) findViewById(R.id.gesturepwd_create_text);
		mLockPatternView.setOnPatternListener(mChooseNewLockPatternListener);
		mLockPatternView.setTactileFeedbackEnabled(true);
		
		mPreviewViews = (LockPatternSmallView) findViewById(R.id.gesturepwd_pattern_preview);
		
		if (savedInstanceState == null) {
			updateStage(Stage.Introduction);
//			updateStage(Stage.HelpScreen);
		} else {
			// restore from previous state
			final String patternString = savedInstanceState.getString(KEY_PATTERN_CHOICE);
			if (patternString != null) {
				mChosenPattern = LockPatternUtils.stringToPattern(patternString);
			}
			updateStage(Stage.values()[savedInstanceState.getInt(KEY_UI_STAGE)]);
		}
	}

	private void updatePreviewViews() {
		if (mChosenPattern == null) {
			return;
		}
		Log.i(TAG, "result = " + mChosenPattern.toString());
		mPreviewViews.setPattern(mChosenPattern);
	}
	
	private void updatePreviewViews(List<Cell> pattern) {
		if ((pattern == null) || (mUiStage != Stage.Introduction)) {
			return;
		}
		mPreviewViews.setPattern(pattern);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(KEY_UI_STAGE, mUiStage.ordinal());
		if (mChosenPattern != null) {
			outState.putString(KEY_PATTERN_CHOICE,
					LockPatternUtils.patternToString(mChosenPattern));
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			if (mUiStage == Stage.HelpScreen) {
				updateStage(Stage.Introduction);
				return true;
			}
		}
		if (keyCode == KeyEvent.KEYCODE_MENU && mUiStage == Stage.Introduction) {
			updateStage(Stage.HelpScreen);
			return true;
		}
		return false;
	}

	private Runnable mClearPatternRunnable = new Runnable() {
		public void run() {
			mLockPatternView.clearPattern();
		}
	};
	
	private Runnable mConfirmPatternRunnable = new Runnable() {
		public void run() {
			if(!isFinishing()) {
				mLockPatternView.setEnabled(true);
				updateStage(Stage.NeedToConfirm);
			}
		}
	};
	
	private Runnable mSaveAndFinishPatternRunnable = new Runnable() {
		public void run() {
			if (!isFinishing()) {
				mLockPatternView.setEnabled(true);
				saveChosenPatternAndFinish();
			}
		}
	};
	
	@SuppressWarnings("unused")
	private Runnable mHelpFinishPatternRunnable = new Runnable() {
		public void run() {
			if (!isFinishing()) {
				mLockPatternView.clearPattern();
				mLockPatternView.setDisplayMode(DisplayMode.Correct);
				updateStage(Stage.Introduction);
			}
		}
	};

	protected LockPatternView.OnPatternListener mChooseNewLockPatternListener = new LockPatternView.OnPatternListener() {

		public void onPatternStart() {
			mLockPatternView.removeCallbacks(mClearPatternRunnable);
			patternInProgress();
		}

		public void onPatternCleared() {
			mLockPatternView.removeCallbacks(mClearPatternRunnable);
		}

		public void onPatternDetected(List<LockPatternView.Cell> pattern) {
			if (pattern == null) return;
			
			if (mUiStage == Stage.NeedToConfirm
					|| mUiStage == Stage.ConfirmWrong) {
				if (mChosenPattern == null)
					throw new IllegalStateException(
							"null chosen pattern in stage 'need to confirm");
				if (mChosenPattern.equals(pattern)) {
					mLockPatternView.setEnabled(false);
					mLockPatternView.postDelayed(mSaveAndFinishPatternRunnable, 250);
				} else {
					updateStage(Stage.ConfirmWrong);
				}
			} else if (mUiStage == Stage.Introduction
					|| mUiStage == Stage.ChoiceTooShort) {
				if (pattern.size() < LockPatternUtils.MIN_LOCK_PATTERN_SIZE) {
					updateStage(Stage.ChoiceTooShort);
				} else {
					mChosenPattern = new ArrayList<LockPatternView.Cell>(pattern);
					updateStage(Stage.FirstChoiceValid);
					
					mLockPatternView.setEnabled(false);
					mLockPatternView.postDelayed(mConfirmPatternRunnable, 1000);
				}
			} else {
				throw new IllegalStateException("Unexpected stage " + mUiStage
						+ " when " + "entering the pattern.");
			}
		}

		public void onPatternCellAdded(List<Cell> pattern) {
			updatePreviewViews(pattern);
		}

		private void patternInProgress() {
			mHeaderText.setText(R.string.lockpattern_recording_inprogress);
		}
	};

	private void updateStage(Stage stage) {
		mUiStage = stage;
		if (stage == Stage.ChoiceTooShort) {
			mHeaderText.setText(getResources().getString(stage.headerMessage,
					LockPatternUtils.MIN_LOCK_PATTERN_SIZE));
		} else {
			mHeaderText.setText(stage.headerMessage);
		}

		// same for whether the patten is enabled
		if (stage.patternEnabled) {
			mLockPatternView.enableInput();
		} else {
			mLockPatternView.disableInput();
		}

		mLockPatternView.setDisplayMode(DisplayMode.Correct);

		switch (mUiStage) {
		case Introduction:
			mLockPatternView.clearPattern();
			break;
		case HelpScreen:
			mLockPatternView.setPattern(DisplayMode.Animate, mAnimatePattern);
			break;
		case ChoiceTooShort:
			mLockPatternView.setDisplayMode(DisplayMode.Wrong);
			postClearPatternRunnable();
			break;
		case FirstChoiceValid:
			break;
		case NeedToConfirm:
			mLockPatternView.clearPattern();
			updatePreviewViews();
			break;
		case ConfirmWrong:
			mLockPatternView.setDisplayMode(DisplayMode.Wrong);
			postClearPatternRunnable();
			break;
		case ChoiceConfirmed:
			break;
		}
	}

	// clear the wrong pattern unless they have started a new one
	// already
	private void postClearPatternRunnable() {
		mLockPatternView.removeCallbacks(mClearPatternRunnable);
		mLockPatternView.postDelayed(mClearPatternRunnable, 1000);
	}

	private void saveChosenPatternAndFinish() {
		App.getInstance().getLockPatternUtils().saveLockPattern(mChosenPattern);
		showToast("密码设置成功");
		startActivity(new Intent(this,UnlockGesturePasswordActivity.class));
		finish();
	}
}
