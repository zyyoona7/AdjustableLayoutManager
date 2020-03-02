package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

/**
 * 可以指定 itemType 宽/高 可以根据其他 item 宽/高动态伸缩，
 * 如果当前所有 item 没有铺满 RecyclerView 则会使 {@link #adjustableItemType} 的 item 铺满剩余空间
 * 这种 itemType 的item只能有一个，如果同时存在多个取{@link #minAdjustableItemSize}
 */
public class AdjustableLayoutManager extends LinearLayoutManager {

    private static final String TAG = "AdjustableLayoutManager";

    private static final int NO_ITEM_TYPE = -11221;
    private static final int MIN_SIZE_NO_ITEM_TYPE = -2;

    /**
     * 根据其他 item 调整的 itemType（主角 itemType）
     */
    private int adjustableItemType = NO_ITEM_TYPE;
    /**
     * 根据其他 item 调整的 itemType 的下标，优化测量
     */
    private int adjustableItemPosition = NO_POSITION;
    /**
     * 最小伸缩尺寸
     */
    private int minAdjustableItemSize = -1;
    /**
     * 最小伸缩尺寸的宽/高比例，minAdjustableItemRatio * (宽或高) = minAdjustableItemSize
     */
    private float minAdjustableItemRatio = 0f;
    /**
     * 最小尺寸
     */
    private int minSize = minAdjustableItemSize;
    /**
     * 可调控 item 自身大小
     */
    private int adjustableItemSize = 0;

    private boolean isNeedResize = false;

    public AdjustableLayoutManager(Context context) {
        super(context);
    }

    public AdjustableLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }

    public AdjustableLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        super.onLayoutChildren(recycler, state);
        if (getChildCount() == state.getItemCount() && adjustableItemType != NO_ITEM_TYPE) {
            if (adjustableItemPosition != NO_POSITION && !isAdjustablePosition()) {
                //如果设置了可调整itemType的下标，并且当前下标不是可调整类型直接返回
                return;
            }
            minSize = getAdjustableItemMinSize(state);
            if (minSize == MIN_SIZE_NO_ITEM_TYPE) {
                return;
            }
            isNeedResize = true;
            super.onLayoutChildren(recycler, state);
            isNeedResize = false;
        }
    }

    private boolean isAdjustablePosition() {
        if (adjustableItemPosition < getChildCount() && adjustableItemPosition >= 0) {
            return isAdjustableItem(getChildAt(adjustableItemPosition));
        }
        return false;
    }

    /**
     * 除了 {@link #adjustViewSize} 方法外全部来自于 LinearLayoutManager
     *
     * @param recycler    recycler
     * @param state       state
     * @param layoutState layoutState
     * @param result      result
     */
    @Override
    void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state, LayoutState layoutState, LayoutChunkResult result) {
        View view = layoutState.next(recycler);
        if (view == null) {
            if (DEBUG && layoutState.mScrapList == null) {
                throw new RuntimeException("received null view when unexpected");
            }
            // if we are laying out views in scrap, this may return null which means there is
            // no more items to layout.
            result.mFinished = true;
            return;
        }
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
        if (layoutState.mScrapList == null) {
            if (mShouldReverseLayout == (layoutState.mLayoutDirection
                    == LayoutState.LAYOUT_START)) {
                addView(view);
            } else {
                addView(view, 0);
            }
        } else {
            if (mShouldReverseLayout == (layoutState.mLayoutDirection
                    == LayoutState.LAYOUT_START)) {
                addDisappearingView(view);
            } else {
                addDisappearingView(view, 0);
            }
        }

        //根据条件调整 view 的大小
        adjustViewSize(view);

        measureChildWithMargins(view, 0, 0);
        result.mConsumed = mOrientationHelper.getDecoratedMeasurement(view);
        int left, top, right, bottom;
        if (mOrientation == VERTICAL) {
            if (isLayoutRTL()) {
                right = getWidth() - getPaddingRight();
                left = right - mOrientationHelper.getDecoratedMeasurementInOther(view);
            } else {
                left = getPaddingLeft();
                right = left + mOrientationHelper.getDecoratedMeasurementInOther(view);
            }
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                bottom = layoutState.mOffset;
                top = layoutState.mOffset - result.mConsumed;
            } else {
                top = layoutState.mOffset;
                bottom = layoutState.mOffset + result.mConsumed;
            }
        } else {
            top = getPaddingTop();
            bottom = top + mOrientationHelper.getDecoratedMeasurementInOther(view);

            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                right = layoutState.mOffset;
                left = layoutState.mOffset - result.mConsumed;
            } else {
                left = layoutState.mOffset;
                right = layoutState.mOffset + result.mConsumed;
            }
        }
        // We calculate everything with View's bounding box (which includes decor and margins)
        // To calculate correct layout position, we subtract margins.
        layoutDecoratedWithMargins(view, left, top, right, bottom);
        if (DEBUG) {
            Log.d(TAG, "laid out child at position " + getPosition(view) + ", with l:"
                    + (left + params.leftMargin) + ", t:" + (top + params.topMargin) + ", r:"
                    + (right - params.rightMargin) + ", b:" + (bottom - params.bottomMargin));
        }
        // Consume the available space if the view is not removed OR changed
        if (params.isItemRemoved() || params.isItemChanged()) {
            result.mIgnoreConsumed = true;
        }
        result.mFocusable = view.hasFocusable();
    }

    /**
     * 调整 View 的大小
     *
     * @param view child view
     */
    private void adjustViewSize(View view) {
        if (!isNeedResize) {
            if (isAdjustableItem(view)) {
                setViewSize(view, getMinItemSize(view));
            }
            return;
        }
        if (isAdjustableItem(view)) {
            if (minSize > 0) {
                setViewSize(view, minSize);
            } else {
                setViewSize(view, adjustableItemSize);
            }
        }
    }

    /**
     * 获取可调整 item 的最小宽/高
     *
     * @param state state
     * @return 可调整 item 的最小宽/高
     */
    private int getAdjustableItemMinSize(RecyclerView.State state) {
        int minSize = minAdjustableItemSize;

        if (state.getItemCount() != getChildCount()) {
            return minSize;
        }
        int adjustableCount = 0;
        int minUsedSize = 0;
        for (int i = 0; i < state.getItemCount(); i++) {

            View childView = getChildAt(i);

            if (childView == null || childView.getLayoutParams() == null) {
                continue;
            }
            //先测量一次，获取 childView 真实的尺寸
            measureChildWithMargins(childView, 0, 0);

            if (isAdjustableItem(childView)) {
                //因为这个是需要设置 view 大小的值，所以不使用 getDecoratedMeasuredSize()
                setViewSize(childView, getMinItemSize(childView));
                //设置完尺寸后重新测量
                measureChildWithMargins(childView, 0, 0);
                adjustableItemSize = Math.max(getMeasuredSize(childView), adjustableItemSize);
                adjustableCount++;
            }
            int childSize = getDecoratedMeasuredSize(childView);
            minUsedSize += childSize;
        }

        int parentSize = mOrientation == RecyclerView.VERTICAL ? getHeight() : getWidth();
        if (minUsedSize < parentSize) {
            minSize = parentSize - (minUsedSize - adjustableItemSize);
        }
        return adjustableCount == 0 ? MIN_SIZE_NO_ITEM_TYPE :
                (adjustableCount == 1 ? minSize : minAdjustableItemSize);
    }

    private int getMinItemSize(View childView) {
        if (minAdjustableItemSize > 0) {
            return minAdjustableItemSize;
        } else {
            if (minAdjustableItemRatio <= 0) {
                minAdjustableItemRatio = 1f;
            }
            return (int) (minAdjustableItemRatio * getMeasuredSizeForRatio(childView));
        }
    }

    /**
     * 设置 View 大小
     *
     * @param view child view
     * @param size size
     */
    private void setViewSize(View view, int size) {
        if (size <= 0) {
            return;
        }
        if (mOrientation == RecyclerView.VERTICAL) {
            view.getLayoutParams().height = size;
        } else {
            view.getLayoutParams().width = size;
        }
    }

    /**
     * 获取测量后的尺寸
     *
     * @param childView child view
     * @return 测量后的尺寸
     */
    private int getMeasuredSize(View childView) {
        return mOrientation == RecyclerView.VERTICAL ?
                childView.getMeasuredHeight() : childView.getMeasuredWidth();
    }

    /**
     * 获取 ratio 需要的尺寸
     *
     * @param childView child view
     * @return ratio 需要的尺寸
     */
    private int getMeasuredSizeForRatio(View childView) {
        return mOrientation == RecyclerView.VERTICAL ? childView.getMeasuredWidth() : childView.getMeasuredHeight();
    }

    /**
     * 获取测量后的尺寸（带有分割线尺寸）
     *
     * @param childView child view
     * @return 测量后的尺寸（带有分割线尺寸）
     */
    private int getDecoratedMeasuredSize(View childView) {
        return mOrientation == RecyclerView.VERTICAL ?
                getDecoratedMeasuredHeight(childView) : getDecoratedMeasuredWidth(childView);
    }

    /**
     * 当前 View 是否是可调整尺寸类型
     *
     * @param childView child view
     * @return 是否是可调整尺寸类型
     */
    private boolean isAdjustableItem(View childView) {
        RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(childView);
        return viewHolder != null && adjustableItemType == viewHolder.getItemViewType();
    }

    /**
     * 设置可调整尺寸 itemType
     *
     * @param adjustableItemType itemType
     */
    public void setAdjustableItemType(int adjustableItemType) {
        if (adjustableItemType == this.adjustableItemType) {
            return;
        }
        this.adjustableItemType = adjustableItemType;
        requestLayout();
    }

    /**
     * 设置最小可接受的 itemSize
     *
     * @param minAdjustableItemSize itemSize
     */
    public void setMinAdjustableItemSize(int minAdjustableItemSize) {
        if (minAdjustableItemSize == this.minAdjustableItemSize) {
            return;
        }
        this.minAdjustableItemSize = minAdjustableItemSize;
        requestLayout();
    }

    /**
     * 设置最小可接受的 itemSize 比例
     *
     * @param minAdjustableItemRatio 比例，纵向则会乘以 view 的宽度，横向则会乘以高度
     */
    public void setMinAdjustableItemRatio(float minAdjustableItemRatio) {
        if (minAdjustableItemRatio == this.minAdjustableItemRatio) {
            return;
        }
        this.minAdjustableItemRatio = minAdjustableItemRatio;
        requestLayout();
    }

    /**
     * 设置可调整尺寸 item 所在的下标
     *
     * @param adjustableItemPosition 下标
     */
    public void setAdjustableItemPosition(int adjustableItemPosition) {
        this.adjustableItemPosition = adjustableItemPosition;
    }
}
