package com.android.lockpattern.widget;


import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import com.android.lockpattern.R;
import com.android.lockpattern.widget.LockPatternView.Cell;

/**
 * Displays and detects the user's unlock attempt, which is a drag of a finger
 * across regions of the screen.
 *
 * Is also capable of displaying a static pattern in "in progress", "wrong" or
 * "correct" states.
 */
public class LockPatternSmallView extends View {
    // Aspect to use when rendering this view
    private static final int ASPECT_SQUARE = 0; // View will be the minimum of width/height
    private static final int ASPECT_LOCK_WIDTH = 1; // Fixed width; height will be minimum of (w,h)
    private static final int ASPECT_LOCK_HEIGHT = 2; // Fixed height; width will be minimum of (w,h)

    private Paint mPaint = new Paint();
    private Paint mPathPaint = new Paint();

    private byte mPatternSize = LockPatternUtils.getRowOrColCount();
    private ArrayList<Cell> mPattern = new ArrayList<Cell>(mPatternSize * mPatternSize);

    /**
     * Lookup table for the circles of the pattern we are currently drawing.
     * This will be the cells of the complete pattern unless we are animating,
     * in which case we use this to hold the cells we are drawing for the in
     * progress animation.
     */
    private boolean[][] mPatternDrawLookup = new boolean[mPatternSize][mPatternSize];

    /**
     * the in progress point:
     * - during interaction: where the user's finger is
     * - during animation: the current tip of the animating line
     */
    private float mInProgressX = -1;
    private float mInProgressY = -1;

    private boolean mInputEnabled = true;
    private boolean mInStealthMode = true;
    private boolean mPatternInProgress = false;
    private boolean mVisibleDots = true;
    private boolean mShowErrorPath = true;

    private float mDiameterFactor = 0.10f; // TODO: move to attrs
    private final int mStrokeAlpha = 128;

    private float mSquareWidth;
    private float mSquareHeight;

    private Bitmap mBitmapCircleGreen;
    private Bitmap mBitmapCircleDefault;
    
    private final Path mCurrentPath = new Path();

    private int mBitmapWidth;
    private int mBitmapHeight;

    private int mAspect;
    private final Matrix mCircleMatrix = new Matrix();

    public LockPatternSmallView(Context context) {
        this(context, null);
    }

    public LockPatternSmallView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LockPatternView);

        final String aspect = a.getString(R.styleable.LockPatternView_aspect);

        if ("square".equals(aspect)) {
            mAspect = ASPECT_SQUARE;
        } else if ("lock_width".equals(aspect)) {
            mAspect = ASPECT_LOCK_WIDTH;
        } else if ("lock_height".equals(aspect)) {
            mAspect = ASPECT_LOCK_HEIGHT;
        } else {
            mAspect = ASPECT_SQUARE;
        }

        setClickable(true);

        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);
        mPathPaint.setColor(Color.WHITE);   // TODO this should be from the style
        mPathPaint.setAlpha(mStrokeAlpha);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);

        // lot's of bitmaps!
		mBitmapCircleDefault = getBitmapFor(R.drawable.gesture_pattern_item_bg);
		mBitmapCircleGreen = getBitmapFor(R.drawable.gesture_pattern_selected);

        // bitmaps have the size of the largest bitmap in this group
		final Bitmap bitmaps[] = { mBitmapCircleDefault, mBitmapCircleGreen };

		for (Bitmap bitmap : bitmaps) {
			mBitmapWidth = Math.max(mBitmapWidth, bitmap.getWidth());
			mBitmapHeight = Math.max(mBitmapHeight, bitmap.getHeight());
		}
    }

    private Bitmap getBitmapFor(int resId) {
        return BitmapFactory.decodeResource(getContext().getResources(), resId);
    }

    /**
     * Set the pattern size of the lockscreen
     *
     * @param size The pattern size.
     */
    public void setLockPatternSize(byte size) {
        mPatternSize = size;
        Cell.updateSize(size);
        mPattern = new ArrayList<Cell>(size * size);
        mPatternDrawLookup = new boolean[size][size];
    }

    public void setPattern(List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Cell cell : pattern) {
            mPatternDrawLookup[cell.getRow()][cell.getColumn()] = true;
        }
		invalidate(getLeft(), getTop(), getRight(), getBottom());
    }
    
    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        invalidate();
    }

    /**
     * Clear the pattern lookup table.
     */
    private void clearPatternDrawLookup() {
        for (int i = 0; i < mPatternSize; i++) {
            for (int j = 0; j < mPatternSize; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }

    /**
     * Disable input (for instance when displaying a message that will
     * timeout so user doesn't get view into messy state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - getPaddingLeft() - getPaddingRight();
        mSquareWidth = width / (float) mPatternSize;

        final int height = h - getPaddingTop() - getPaddingBottom();
        mSquareHeight = height / (float) mPatternSize;
    }

	private int resolveMeasured(int measureSpec, int desired) {
		int result = 0;
		int specSize = MeasureSpec.getSize(measureSpec);
		switch (MeasureSpec.getMode(measureSpec)) {
		case MeasureSpec.UNSPECIFIED:
			result = desired;
			break;
		case MeasureSpec.AT_MOST:
			result = Math.max(specSize, desired);
			break;
		case MeasureSpec.EXACTLY:
		default:
			result = specSize;
		}
		return result;
	}

    @Override
    protected int getSuggestedMinimumWidth() {
        // View should be large enough to contain side-by-side target bitmaps
        return mPatternSize * mBitmapWidth;
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        // View should be large enough to contain side-by-side target bitmaps
        return mPatternSize * mBitmapWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);

        switch (mAspect) {
            case ASPECT_SQUARE:
                viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_WIDTH:
                viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_HEIGHT:
                viewWidth = Math.min(viewWidth, viewHeight);
                break;
        }
        // Log.v(TAG, "LockPatternView dimensions: " + viewWidth + "x" + viewHeight);
        setMeasuredDimension(viewWidth, viewHeight);
    }


    private float getCenterXForColumn(int column) {
        return getPaddingLeft() + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return getPaddingTop() + row * mSquareHeight + mSquareHeight / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final ArrayList<Cell> pattern = mPattern;
        final int count = pattern.size();
        final boolean[][] drawLookup = mPatternDrawLookup;

        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        float radius = (squareWidth * mDiameterFactor * 0.5f);
        mPathPaint.setStrokeWidth(radius);

        final Path currentPath = mCurrentPath;
        currentPath.rewind();

        // TODO: the path should be created and cached every time we hit-detect a cell
        // only the last segment of the path should be computed here
        // draw the path of the pattern (unless the user is in progress, and
        // we are in stealth mode)
		final boolean drawPath = !mInStealthMode;

        // draw the arrows associated with the path (unless the user is in progress, and
        // we are in stealth mode)
        boolean oldFlag = (mPaint.getFlags() & Paint.FILTER_BITMAP_FLAG) != 0;
        mPaint.setFilterBitmap(true); // draw with higher quality since we render with transforms

        if (drawPath) {
            boolean anyCircles = false;
            for (int i = 0; i < count; i++) {
                Cell cell = pattern.get(i);

                // only draw the part of the pattern stored in
                // the lookup table (this is only different in the case
                // of animation).
                if (!drawLookup[cell.row][cell.column]) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(cell.column);
                float centerY = getCenterYForRow(cell.row);
                if (i == 0) {
                    currentPath.moveTo(centerX, centerY);
                } else {
                    currentPath.lineTo(centerX, centerY);
                }
            }

            // add last in progress section
            if ((mPatternInProgress) && anyCircles) {
                currentPath.lineTo(mInProgressX, mInProgressY);
            }
            canvas.drawPath(currentPath, mPathPaint);
        }
        
        // draw the circles
        final int paddingTop = getPaddingTop();
        final int paddingLeft = getPaddingLeft();

        for (int i = 0; i < mPatternSize; i++) {
            float topY = paddingTop + i * squareHeight;
            for (int j = 0; j < mPatternSize; j++) {
                float leftX = paddingLeft + j * squareWidth;
                drawCircle(canvas, (int) leftX, (int) topY, drawLookup[i][j]);
            }
        }

        mPaint.setFilterBitmap(oldFlag); // restore default flag
    }

    /**
     * @param canvas
     * @param leftX
     * @param topY
     * @param partOfPattern Whether this circle is part of the pattern.
     */
    private void drawCircle(Canvas canvas, int leftX, int topY, boolean partOfPattern) {
        Bitmap outerCircle = mBitmapCircleDefault;
        Bitmap innerCircle = null;
		if (!partOfPattern
				|| (!mInStealthMode)) {
			// unselected circle
			innerCircle = null;
		} else if(mInStealthMode){
			// user is in middle of drawing a pattern
			innerCircle = mBitmapCircleGreen;
		}

        final int width = mBitmapWidth;
        final int height = mBitmapHeight;

        final float squareWidth = mSquareWidth;
        final float squareHeight = mSquareHeight;

        int offsetX = (int) ((squareWidth - width) / 2f);
        int offsetY = (int) ((squareHeight - height) / 2f);

        // Allow circles to shrink if the view is too small to hold them.
        float sx = Math.min(mSquareWidth / mBitmapWidth, 1.0f);
        float sy = Math.min(mSquareHeight / mBitmapHeight, 1.0f);

		mCircleMatrix.setTranslate(leftX + offsetX, topY + offsetY);
		mCircleMatrix.preTranslate(mBitmapWidth / 2, mBitmapHeight / 2);
		mCircleMatrix.preScale(sx, sy);
		mCircleMatrix.preTranslate(-mBitmapWidth / 2, -mBitmapHeight / 2);

        canvas.drawBitmap(outerCircle, mCircleMatrix, mPaint);
        
		if (innerCircle != null) {
			canvas.drawBitmap(innerCircle, mCircleMatrix, mPaint);
		}
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                LockPatternUtils.patternToString(mPattern),
                mPatternSize,
                mInputEnabled, mInStealthMode, mVisibleDots, mShowErrorPath);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setPattern(LockPatternUtils.stringToPattern(ss.getSerializedPattern()));
        mPatternSize = ss.getPatternSize();
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mVisibleDots = ss.isVisibleDots();
        mShowErrorPath = ss.isShowErrorPath();
    }

    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final byte mPatternSize;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mVisibleDots;
        private final boolean mShowErrorPath;

        /**
         * Constructor called from {@link LockPatternSmallView#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, String serializedPattern,
                byte patternSize, boolean inputEnabled, boolean inStealthMode,
                boolean visibleDots, boolean showErrorPath) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mPatternSize = patternSize;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mVisibleDots = visibleDots;
            mShowErrorPath = showErrorPath;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mPatternSize = (byte) in.readByte();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mVisibleDots = (Boolean) in.readValue(null);
            mShowErrorPath = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public byte getPatternSize() {
            return mPatternSize;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isVisibleDots() {
            return mVisibleDots;
        }

        public boolean isShowErrorPath() {
            return mShowErrorPath;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeByte(mPatternSize);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mVisibleDots);
            dest.writeValue(mShowErrorPath);
        }

        @SuppressWarnings("unused")
		public static final Parcelable.Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}