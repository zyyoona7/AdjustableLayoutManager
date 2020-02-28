package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * 可以指定 itemType 宽/高 可以根据其他 item 宽/高动态伸缩，
 * 如果当前所有 item 没有铺满 RecyclerView 则会使 {@link #adjustableItemType} 的 item 铺满剩余空间
 * 这种 itemType 的item只能有一个，如果同时存在多个取{@link #minAdjustableItemSize}
 */
public class AdjustableLayoutManager extends LinearLayoutManager {

    private static final String TAG = "AdjustableLayoutManager";

    private static final int DEFAULT_ADJUSTABLE_COUNT = 12;
    private static final int NO_ITEM_TYPE = -11221;

    /**
     * 根据其他 item 调整的 itemType（主角 itemType）
     */
    private int adjustableItemType = NO_ITEM_TYPE;
    /**
     * 上一次调整的 itemType
     */
    private int oldAdjustableItemType = NO_ITEM_TYPE;
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
     * 触发拉伸测量的 itemCount，如果当前 itemCount > maxAdjustableItemCount 则不会触发逐个测量
     */
    private int maxAdjustableItemCount = DEFAULT_ADJUSTABLE_COUNT;

    /*
      -------- 以下属性为了不满足条件时还原宽/高 --------
     */
    /**
     * 原可调控 item 真实大小
     */
    private int realOldAdjustableItemSize = 0;
    /**
     * 当前 itemType 真实大小
     */
    private int realAdjustableItemSize = 0;
    /**
     * 可调控 item 自身大小
     */
    private int adjustableItemSize = 0;
    /*
      -------- 以上属性为了不满足条件时还原宽/高 --------
     */
    /**
     * 当 itemCount 不满足调整大小时是否重置已经改变的 item 大小
     */
    private boolean isResizeWhenItemCountDisallow = false;


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
        if (!state.isPreLayout()) {
            minSize = getAdjustableItemMinSize(recycler, state);
        }
        super.onLayoutChildren(recycler, state);
        if (!isAllowItemCount(state) && isResizeWhenItemCountDisallow) {
            oldAdjustableItemType = NO_ITEM_TYPE;
            realOldAdjustableItemSize = 0;
            realAdjustableItemSize = 0;
        }
    }

    /**
     * 除了 {@link #adjustViewSize} 方法外全部来自于 LinearLayoutManager
     *
     * @param recycler
     * @param state
     * @param layoutState
     * @param result
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
        adjustViewSize(state, view);

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
     * @param state state
     * @param view  child view
     */
    private void adjustViewSize(RecyclerView.State state, View view) {
        if (isAllowItemCount(state)) {
            if (isOldAdjustableItem(view)) {
                setViewSize(view, realOldAdjustableItemSize);
            }
            if (isAdjustableItem(view)) {
                if (minSize > 0) {
                    setViewSize(view, minSize);
                } else {
                    setViewSize(view, adjustableItemSize);
                }
            }
        } else if (isResizeWhenItemCountDisallow) {
            if (isOldAdjustableItem(view)) {
                setViewSize(view, realOldAdjustableItemSize);
            }
            if (isAdjustableItem(view)) {
                setViewSize(view, realAdjustableItemSize);
            }
        }
    }

    /**
     * 获取可调整 item 的最小宽/高
     *
     * @param recycler recycler
     * @param state    state
     * @return 可调整 item 的最小宽/高
     */
    private int getAdjustableItemMinSize(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int minSize = minAdjustableItemSize;

        if (!isAllowItemCount(state)) {
            return minSize;
        }
        int adjustableCount = 0;
        int minUsedSize = 0;
        for (int i = 0; i < state.getItemCount(); i++) {

            View childView = recycler.getViewForPosition(i);

            if (childView.getLayoutParams() == null) {
                continue;
            }
            //先测量一次，获取 childView 真实的尺寸
            measureChildWithMargins(childView, 0, 0);

            if (isOldAdjustableItem(childView)) {
                //因为这个是需要设置 view 大小的值，所以不使用 getDecoratedMeasuredSize()
                realOldAdjustableItemSize = getMeasuredSize(childView);
            }
            if (isAdjustableItem(childView)) {
                //因为这个是需要设置 view 大小的值，所以不使用 getDecoratedMeasuredSize()
                realAdjustableItemSize = getMeasuredSize(childView);
                if (minAdjustableItemSize > 0) {
                    setViewSize(childView, minAdjustableItemSize);
                    //设置完尺寸后重新测量
                    measureChildWithMargins(childView, 0, 0);
                } else if (minAdjustableItemRatio > 0) {
                    setViewSize(childView, (int) (minAdjustableItemRatio * getMeasuredSizeForRatio(childView)));
                    //设置完尺寸后重新测量
                    measureChildWithMargins(childView, 0, 0);
                }
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
        return adjustableCount == 1 ? minSize : minAdjustableItemSize;
    }

    /**
     * itemCount 是否在测量范围内
     *
     * @param state state
     * @return itemCount 是否在测量范围内
     */
    private boolean isAllowItemCount(RecyclerView.State state) {
        return state.getItemCount() <= maxAdjustableItemCount;
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
     * 当前 View 是否是上一次可调整尺寸类型
     *
     * @param childView child view
     * @return 是否是上一次可调整尺寸类型
     */
    private boolean isOldAdjustableItem(View childView) {
        RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(childView);
        return viewHolder != null && oldAdjustableItemType == viewHolder.getItemViewType();
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
        this.oldAdjustableItemType = this.adjustableItemType;
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
     * 设置计算可调整 itemCount ，超过此数量不会触发额外的测量操作
     *
     * @param maxAdjustableItemCount itemCount
     */
    public void setMaxAdjustableItemCount(int maxAdjustableItemCount) {
        if (maxAdjustableItemCount == this.maxAdjustableItemCount) {
            return;
        }
        this.maxAdjustableItemCount = maxAdjustableItemCount;
        requestLayout();
    }

    /**
     * 如果 adapter 的 itemCount 超过 {@link #maxAdjustableItemCount}
     * 并且 {@link #isResizeWhenItemCountDisallow} 为 true 则会恢复 View 的大小
     *
     * @param resizeWhenItemCountDisallow itemCound 超过 {@link #maxAdjustableItemCount} 是否重置 view 大小
     */
    public void setResizeWhenItemCountDisallow(boolean resizeWhenItemCountDisallow) {
        if (resizeWhenItemCountDisallow == this.isResizeWhenItemCountDisallow) {
            return;
        }
        isResizeWhenItemCountDisallow = resizeWhenItemCountDisallow;
    }
}
