package org.dync.ijkplayerlib.widget.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.FloatRange;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;

import org.dync.ijkplayerlib.widget.media.IRenderView;
import org.dync.ijkplayerlib.widget.media.IjkVideoView;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by KathLine on 2017/8/22.
 */
public class PlayerController {

    /**
     * 打印日志的TAG
     */
    private static final String TAG = PlayerController.class.getSimpleName();
    /**
     * 全局上下文
     */
    private final Context mContext;
    /**
     * 依附的容器Activity
     */
    private final Activity mActivity;
    /**
     * 播放器的父布局
     */
    private View videoParentLayout;
    /**
     * 播放器的父布局的显示比例
     */
    private int aspectRatio;
    /**
     * 原生的Ijkplayer
     */
    private IjkVideoView videoView;

    private SeekBar videoController;

    /**
     * 当前播放位置
     */
    private int currentPosition;
    /**
     * 滑动进度条得到的新位置，和当前播放位置是有区别的,newPosition =0也会调用设置的，故初始化值为-1
     */
    private long newPosition = -1;
    /**
     * 视频旋转的角度，默认只有0,90.270分别对应向上、向左、向右三个方向
     */
    private int rotation = 0;
    /**
     * 视频显示比例,默认保持原视频的大小
     */
    private int currentShowType = IRenderView.AR_ASPECT_FIT_PARENT;
    /**
     * 播放总时长
     */
    private long duration;
    /**
     * 当前声音大小
     */
    private int volume;
    /**
     * 设备最大音量
     */
    private int mMaxVolume;
    /**
     * 获取当前设备的宽度
     */
    private int screenWidthPixels;
    /**
     * 记录播放器竖屏时的高度
     */
    private int initHeight;
    /**
     * 当前亮度大小
     */
    private float brightness;
    /**
     * 当前播放地址
     */
    private String currentUrl;
    /**
     * 当前选择的视频流索引
     */
    private int currentSelect;
    /**
     * 记录进行后台时的播放状态0为播放，1为暂停
     */
    private int bgState;
    /**
     * 第三方so是否支持，默认不支持，true为支持
     */
    private boolean playerSupport;
    /**
     * 是否是直播 默认为非直播，true为直播false为点播，根据isLive()方法前缀rtmp或者后缀.m3u8判断得出的为直播，比较片面，有好的建议欢迎交流
     */
    private boolean isLive;
    /**
     * 是否显示控制面板，默认为隐藏，true为显示false为隐藏
     */
    private boolean isShowControlPanl;
    /**
     * 禁止触摸，默认可以触摸，true为禁止false为可触摸
     */
    private boolean isForbidTouch;
    /**
     * 禁止收起控制面板，默认可以收起，true为禁止false为可触摸
     */
    private boolean isForbidHideControlPanl;
    /**
     * 当前是否切换视频流，默认为否，true是切换视频流，false没有切换
     */
    private boolean isHasSwitchStream;
    /**
     * 是否在拖动进度条中，默认为停止拖动，true为在拖动中，false为停止拖动
     */
    private boolean isDragging;
    /**
     * 播放的时候是否需要网络提示，默认显示网络提示，true为显示网络提示，false不显示网络提示
     */
    private boolean isGNetWork = true;

    private boolean isCharge;
    private int maxPlaytime;
    /**
     * 是否只有全屏，默认非全屏，true为全屏，false为非全屏
     */
    private boolean isOnlyFullScreen;
    /**
     * 是否禁止双击，默认不禁止，true为禁止，false为不禁止
     */
    private boolean isForbidDoulbeUp;
    /**
     * 是否是竖屏，默认为竖屏，true为竖屏，false为横屏
     */
    private boolean isPortrait = true;
    /**
     * 音频管理器
     */
    private AudioManager audioManager;
    /**
     * 同步进度
     */
    private static final int MESSAGE_SHOW_PROGRESS = 1;
    /**
     * 设置新位置
     */
    private static final int MESSAGE_SEEK_NEW_POSITION = 3;
    /**
     * 隐藏提示的box
     */
    private static final int MESSAGE_HIDE_CENTER_BOX = 4;
    /**
     * 重新播放
     */
    private static final int MESSAGE_RESTART_PLAY = 5;


    /**
     * 消息处理
     */
    @SuppressWarnings("HandlerLeak")
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                /**滑动完成，隐藏滑动提示的box*/
                case MESSAGE_HIDE_CENTER_BOX:

                    break;
                /**滑动完成，设置播放进度*/
                case MESSAGE_SEEK_NEW_POSITION:
                    if (videoView != null && !isLive && newPosition >= 0) {
                        videoView.seekTo((int) newPosition);
                        newPosition = -1;
                    }
                    break;
                /**滑动中，同步播放进度*/
                case MESSAGE_SHOW_PROGRESS:
                    long pos = syncProgress();
                    if (!isDragging && isShowControlPanl) {
                        msg = obtainMessage(MESSAGE_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                /**重新去播放*/
                case MESSAGE_RESTART_PLAY:
                    startPlay();
                    break;
            }
        }
    };

    /**========================================视频的监听方法==============================================*/

    /**
     * 控制面板收起或者显示的轮询监听
     */
    private AutoControlPanelRunnable mAutoControlPanelRunnable = new AutoControlPanelRunnable();
    /**
     * Activity界面方向监听
     */
    private OrientationEventListener orientationEventListener;

    /**
     * 进度条滑动监听
     */
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {

        /**数值的改变*/
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                /**不是用户拖动的，自动播放滑动的情况*/
                return;
            } else {
                long duration = getDuration();
                int position = (int) ((duration * progress * 1.0) / 1000);
                String time = generateTime(position);
            }

        }

        /**开始拖动*/
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isDragging = true;
            mHandler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.stop();
            }
        }

        /**停止拖动*/
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (videoView != null) {
                long duration = getDuration();
                videoView.seekTo((int) ((duration * seekBar.getProgress() * 1.0) / 1000));
                mHandler.removeMessages(MESSAGE_SHOW_PROGRESS);
            }
            isDragging = false;
            mHandler.sendEmptyMessageDelayed(MESSAGE_SHOW_PROGRESS, 1000);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.start(AutoControlPanelRunnable.AUTO_INTERVAL);
            }
        }
    };

    /**
     * 亮度进度条滑动监听
     */
    private final SeekBar.OnSeekBarChangeListener onBrightnessControllerChangeListener = new SeekBar.OnSeekBarChangeListener() {
        /**数值的改变*/
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            setBrightness(progress);
        }

        /**开始拖动*/
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        /**停止拖动*/
        public void onStopTrackingTouch(SeekBar seekBar) {
            brightness = -1;
        }
    };

    public void setBrightness(int value) {
        android.view.WindowManager.LayoutParams layout = this.mActivity.getWindow().getAttributes();
        if (brightness < 0) {
            brightness = mActivity.getWindow().getAttributes().screenBrightness;
            if (brightness <= 0.00f) {
                brightness = 0.50f;
            } else if (brightness < 0.01f) {
                brightness = 0.01f;
            }
        }
        if (value < 1) {
            value = 1;
        }
        if (value > 100) {
            value = 100;
        }
        layout.screenBrightness = 1.0F * (float) value / 100.0F;
        if (layout.screenBrightness > 1.0f) {
            layout.screenBrightness = 1.0f;
        } else if (layout.screenBrightness < 0.01f) {
            layout.screenBrightness = 0.01f;
        }
        this.mActivity.getWindow().setAttributes(layout);
    }


    /**
     * 声音进度条滑动监听
     */
    private final SeekBar.OnSeekBarChangeListener onVolumeControllerChangeListener = new SeekBar.OnSeekBarChangeListener() {

        /**数值的改变*/
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            int index = (int) (mMaxVolume * progress * 0.01);
            if (index > mMaxVolume) {
                index = mMaxVolume;
            } else if (index < 0) {
                index = 0;
            }
            // 变更声音
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
            }
        }

        /**开始拖动*/
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        /**停止拖动*/
        public void onStopTrackingTouch(SeekBar seekBar) {
            volume = -1;
        }
    };

    /**
     * 百分比显示切换
     */
    public PlayerController toggleAspectRatio() {
        if (videoView != null) {
            videoView.toggleAspectRatio();
        }
        return this;
    }

    /**
     * 参考{@link IRenderView#AR_ASPECT_FIT_PARENT}、{@link IRenderView#AR_ASPECT_FILL_PARENT}、{@link IRenderView#AR_ASPECT_WRAP_CONTENT}
     * {@link IRenderView#AR_16_9_FIT_PARENT}、{@link IRenderView#AR_4_3_FIT_PARENT}
     * 设置播放视频的宽高比
     *
     * @param showType 视频显示比例，如果该参数不在s_allAspectRatio数组范围内，则默认使用IRenderView.AR_ASPECT_FILL_PARENTIRenderView.AR_ASPECT_FILL_PARENT值
     */
    public PlayerController setVideoRatio(int showType) {
        currentShowType = showType;
        if (videoView != null) {
            videoView.setAspectRatio(currentShowType);
        }
        return this;
    }

    /**
     * 旋转指定角度
     *
     * @param rotation 参数可以设置 0,90,270
     * @return
     */
    public PlayerController setPlayerRotation(int rotation) {
        if (rotation == 0 || rotation < 90) {
            rotation = 90;
        } else if (rotation == 90 || rotation < 270) {
            rotation = 270;
        } else if (rotation == 270 || rotation > 270) {
            rotation = 0;
        }
        if (videoView != null) {
            videoView.setPlayerRotation(rotation);
            videoView.setAspectRatio(currentShowType);
        }
        return this;
    }

    /**
     * 参考{@link IRenderView#AR_ASPECT_FIT_PARENT}、{@link IRenderView#AR_ASPECT_FILL_PARENT}、{@link IRenderView#AR_ASPECT_WRAP_CONTENT}
     * {@link IRenderView#AR_16_9_FIT_PARENT}、{@link IRenderView#AR_4_3_FIT_PARENT}
     * 设置播放视频父布局的宽高比
     *
     * @param aspectRatio
     * @return
     */
    public PlayerController setVideoParentRatio(int aspectRatio) {
        this.aspectRatio = aspectRatio;
        return this;
    }

    /**
     * 设置一开始显示方向，true：竖屏  false：横屏
     *
     * @param isPortrait
     * @return
     */
    public PlayerController setPortrait(boolean isPortrait) {
        if (isPortrait) {
            onConfigurationPortrait();
        } else {
            onConfigurationLandScape();
        }
        return this;
    }

    /**
     * 设置播放速率，这里仅对支持IjkMediaPlayer播放器，
     * 初始放置玩位置推荐在{@link IjkVideoView#setOnPreparedListener(IMediaPlayer.OnPreparedListener)}
     * 方法内<br/>
     * <code>
     * mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
     *
     * @param rate 0.2~2.0之间
     * @Override public void onPrepared(IMediaPlayer iMediaPlayer) {
     * mPlayerController.setPlayRate(1.5f);
     * }
     * });
     * </code>
     */
    public PlayerController setPlayRate(@FloatRange(from = 0.2, to = 2.0) float rate) {
        if (videoView != null) {
            videoView.setPlayRate(rate);
        }
        return this;
    }

    /**
     * 开始播放
     */
    public PlayerController startPlay() {
        if (isLive) {
            if (videoView != null) {
                videoView.setVideoPath(currentUrl);
                videoView.seekTo(0);
            }
        }
        return this;
    }

    /**
     * 选择要播放的流
     */
    public PlayerController switchStream(int index) {
        isLive();
        if (videoView != null && videoView.isPlaying()) {
            getCurrentPosition();
            videoView.release(false);
        }
        isHasSwitchStream = true;
        return this;
    }

    /**
     * 暂停播放
     */
    public PlayerController pausePlay() {
        getCurrentPosition();
        if (videoView != null) {
            videoView.pause();
        }
        return this;
    }

    /**
     * 停止播放
     */
    public PlayerController stopPlay() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        if (mHandler != null) {
            mHandler.removeMessages(MESSAGE_RESTART_PLAY);
        }
        return this;
    }

    /**
     * 设置播放位置
     */
    public PlayerController seekTo(int playtime) {
        if (videoView != null) {
            videoView.seekTo(playtime);
        }
        return this;
    }

    /**
     * 获取当前播放位置
     */
    public int getCurrentPosition() {
        if (videoView != null && !isLive) {
            currentPosition = videoView.getCurrentPosition();
        } else {
            /**直播*/
            currentPosition = -1;
        }
        return currentPosition;
    }

    /**
     * 获取视频播放总时长
     */
    public long getDuration() {
        if (videoView != null) {
            duration = videoView.getDuration();
            return duration;
        } else {
            return 0;
        }
    }

    /**
     * 设置2/3/4/5G和WiFi网络类型提示，
     *
     * @param isGNetWork true为进行2/3/4/5G网络类型提示
     *                   false 不进行网络类型提示
     */
    public PlayerController setNetWorkTypeTie(boolean isGNetWork) {
        this.isGNetWork = isGNetWork;
        return this;
    }

    /**
     * 设置最大观看时长
     *
     * @param isCharge    true为收费 false为免费即不做限制
     * @param maxPlaytime 最大能播放时长，单位秒
     */
    public PlayerController setChargeTie(boolean isCharge, int maxPlaytime) {
        this.isCharge = isCharge;
        this.maxPlaytime = maxPlaytime * 1000;
        return this;
    }

    public int getMaxPlayTime() {
        return maxPlaytime;
    }

    public boolean isCharge() {
        return isCharge;
    }

    /**==========================================Activity生命周期方法回调=============================*/
    /**
     * @Override protected void onPause() {
     * super.onPause();
     * if (player != null) {
     * player.onPause();
     * }
     * }
     */
    public PlayerController onPause() {
        if (videoView != null) {
            bgState = (videoView.isPlaying() ? 0 : 1);
            getCurrentPosition();
            videoView.pause();
        }
        return this;
    }

    /**
     * @Override protected void onDestroy() {
     * super.onDestroy();
     * if (player != null) {
     * player.onDestroy();
     * }
     * }
     */
    public PlayerController onDestroy() {
        orientationEventListener.disable();
        mHandler.removeMessages(MESSAGE_RESTART_PLAY);
        mHandler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
        if (videoView != null) {
            videoView.stopPlayback();
            videoView.release(true);
        }
        if (playerSupport) {
            IjkMediaPlayer.native_profileEnd();
        }
        return this;
    }

    /**
     * @Override public void onConfigurationChanged(Configuration newConfig) {
     * super.onConfigurationChanged(newConfig);
     * if (player != null) {
     * player.onConfigurationChanged(newConfig);
     * }
     * }
     */
    public PlayerController onConfigurationChanged(final Configuration newConfig) {
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        doOnConfigurationChanged(isPortrait);
        return this;
    }

    /**
     * @Override public void onConfigurationChanged(Configuration newConfig) {
     * super.onConfigurationChanged(newConfig);
     * if (player != null) {
     * player.onConfigurationChanged(newConfig);
     * }
     * }
     */
    public PlayerController onConfigurationChanged() {
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {// 横屏
            Log.e(TAG, "onConfigurationChanged: " + "横屏");
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//全屏
//            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            onConfigurationLandScape();
            isPortrait = false;

        } else if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.e(TAG, "onConfigurationChanged: " + "竖屏");
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//全屏
//            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            onConfigurationPortrait();
            isPortrait = true;
        }
        return this;
    }

    /**
     * 是否是竖屏，默认为竖屏，true为竖屏，false为横屏
     *
     * @return
     */
    public boolean isPortrait() {
        return isPortrait;
    }

    /**
     * 调用该方法前必须调用过{@link #setVideoParentLayout(View)},
     * 参考{@link IRenderView#AR_ASPECT_FIT_PARENT}、{@link IRenderView#AR_ASPECT_FILL_PARENT}、{@link IRenderView#AR_ASPECT_WRAP_CONTENT}
     * {@link IRenderView#AR_16_9_FIT_PARENT}、{@link IRenderView#AR_4_3_FIT_PARENT}
     * 竖屏时回调
     */
    public PlayerController onConfigurationPortrait() {
        float displayAspectRatio;
        switch (aspectRatio) {
            case IRenderView.AR_16_9_FIT_PARENT:
                displayAspectRatio = 16.0f / 9.0f;
                break;
            case IRenderView.AR_4_3_FIT_PARENT:
                displayAspectRatio = 4.0f / 3.0f;
                break;
            case IRenderView.AR_ASPECT_FIT_PARENT:
            case IRenderView.AR_ASPECT_FILL_PARENT:
            case IRenderView.AR_ASPECT_WRAP_CONTENT:
            default:
                displayAspectRatio = 0;
                break;
        }

        if (displayAspectRatio != 0) {
            ViewGroup.LayoutParams params = videoParentLayout.getLayoutParams(); //取控件mRlVideoViewLayout当前的布局参数
            final int width = mActivity.getResources().getDisplayMetrics().widthPixels;
            final int heights = (int) (width / displayAspectRatio);
//            final int heights = (int) (width * 0.5625);
            params.height = heights;// 强制设置控件的大小
            videoParentLayout.setLayoutParams(params); //使设置好的布局参数应用到控件
        } else {
            ViewGroup.LayoutParams params = videoParentLayout.getLayoutParams(); //取控件mRlVideoViewLayout当前的布局参数
            params.height = mActivity.getResources().getDisplayMetrics().heightPixels;
            videoParentLayout.setLayoutParams(params); //使设置好的布局参数应用到控
        }
        return this;
    }

    /**
     * 横屏时回调, 调用该方法前必须调用过{@link #setVideoParentLayout(View)}
     */
    public PlayerController onConfigurationLandScape() {
        ViewGroup.LayoutParams params = videoParentLayout.getLayoutParams(); //取控件mRlVideoViewLayout当前的布局参数
        params.height = mActivity.getResources().getDisplayMetrics().heightPixels;
        videoParentLayout.setLayoutParams(params); //使设置好的布局参数应用到控
        return this;
    }

    /**
     * @Override public void onBackPressed() {
     * if (player != null && player.onBackPressed()) {
     * return;
     * }
     * super.onBackPressed();
     * }
     */
    public boolean onBackPressed() {
        if (!isOnlyFullScreen && getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return true;
        }
        return false;
    }

    /**
     * ==========================================对外的方法=============================
     */

    /**
     * @param activity
     */
    public PlayerController(Activity activity) {
        this.mActivity = activity;
        this.mContext = activity;
    }

    /**
     * 新的调用方法，适用非Activity中使用PlayerView，例如fragment、holder中使用
     */
    public PlayerController(Activity activity, IjkVideoView videoView) {
        this.mActivity = activity;
        this.mContext = activity;
        this.videoView = videoView;
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            IjkMediaPlayer.native_profileBegin("libijkplayer.so");
            playerSupport = true;
        } catch (Throwable e) {
            Log.e(TAG, "loadLibraries error", e);
        }
    }

    /**
     * 设置IjkVideoView
     *
     * @param videoView
     * @return
     */
    public PlayerController setIjkVideoView(IjkVideoView videoView) {
        this.videoView = videoView;
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            IjkMediaPlayer.native_profileBegin("libijkplayer.so");
            playerSupport = true;
        } catch (Throwable e) {
            Log.e(TAG, "loadLibraries error", e);
        }
        return this;
    }

    private GestureListener mGestureListener;

    public interface GestureListener {
        /**
         * 设置快进快退
         *
         * @param newPosition 快进快退后要显示的时间（当前播放时间+快进快退了多少时间）
         * @param duration    总时间
         * @param showDelta   快进快退了多少时间
         */
        void onProgressSlide(long newPosition, long duration, int showDelta);

        /**
         * 设置当前视频的音量，范围0~100
         *
         * @param volume
         */
        void onVolumeSlide(int volume);

        /**
         * 设置当前屏幕的亮度0~100
         *
         * @param brightness
         */
        void onBrightnessSlide(float brightness);

        /**
         * 手指离开屏幕后，可以用于上面方法中控件的隐藏
         */
        void endGesture();
    }

    /**
     * 设置基于内置的GestureDetector进行控制，内置类{@link PlayerGestureListener}，回调{@link GestureListener}，
     *
     * @param listener
     * @return
     */
    public PlayerController setGestureListener(GestureListener listener) {
        mGestureListener = listener;
        return this;
    }

    /**
     * 设置视频音量控制，要设置{@link #setGestureListener(GestureListener)}才能生效
     *
     * @return
     */
    public PlayerController setVolumeController() {
        screenWidthPixels = mContext.getResources().getDisplayMetrics().widthPixels;
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return this;
    }

    /**
     * 设置屏幕亮度控制，要设置{@link #setGestureListener(GestureListener)}才能生效
     *
     * @return
     */
    public PlayerController setBrightnessController() {
        try {
            int e = Settings.System.getInt(this.mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            float progress = 1.0F * (float) e / 255.0F;
            android.view.WindowManager.LayoutParams layout = this.mActivity.getWindow().getAttributes();
            layout.screenBrightness = progress;
            mActivity.getWindow().setAttributes(layout);
        } catch (Settings.SettingNotFoundException var7) {
            var7.printStackTrace();
        }
        return this;
    }

    /**
     * 设置控制视频播放进度的SeekBar
     *
     * @param videoController
     * @return
     */
    public PlayerController setVideoController(SeekBar videoController) {
        this.videoController = videoController;
        videoController.setMax(1000);
        videoController.setOnSeekBarChangeListener(mSeekListener);
        return this;
    }

    /**
     * 设置点击区域，基本上是播放器的父布局
     *
     * @param rootLayout
     * @return
     */
    public PlayerController setVideoParentLayout(View rootLayout) {
        final GestureDetector gestureDetector = new GestureDetector(mContext, new PlayerGestureListener());
        videoParentLayout = rootLayout;
        rootLayout.setClickable(true);
        rootLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        if (mAutoControlPanelRunnable != null) {
                            mAutoControlPanelRunnable.stop();
                        }
                        break;
                }
                if (gestureDetector.onTouchEvent(motionEvent))
                    return true;
                // 处理手势结束
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                        endGesture();
                        break;
                }
                return false;
            }
        });


        orientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 0 && orientation <= 30 || orientation >= 330 || (orientation >= 150 && orientation <= 210)) {
                    //竖屏
                    if (isPortrait) {
                        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                } else if ((orientation >= 90 && orientation <= 120) || (orientation >= 240 && orientation <= 300)) {
                    if (!isPortrait) {
                        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                }
            }
        };
        if (isOnlyFullScreen) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        isPortrait = (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        initHeight = rootLayout.getLayoutParams().height;

        return this;
    }

    public interface SyncProgressListener {
        /**
         * 同步视频时间
         *
         * @param position 当前视频播放的时间
         * @param duration 视频的总时间
         */
        void syncTime(long position, long duration);
    }

    private SyncProgressListener syncProgressListener;

    /**
     * 设置视频播放进度的回调
     *
     * @param listener
     * @return
     */
    public PlayerController setSyncProgressListener(SyncProgressListener listener) {
        syncProgressListener = listener;
        return this;
    }

    /**
     * 对面板进行控制，单击{@link #setVideoParentLayout(View)}区域内生效
     *
     * @param listener
     * @return
     */
    public PlayerController setPanelControl(PanelControlListener listener) {
        panelControlListener = listener;
        mHandler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
        if (mAutoControlPanelRunnable != null) {
            mAutoControlPanelRunnable.start(0);
        }
        return this;
    }

    private PanelControlListener panelControlListener;

    public interface PanelControlListener {
        /**
         * 控制操作面板的显示隐藏
         *
         * @param isShowControlPanel true：显示 false：隐藏
         */
        void operatorPanel(boolean isShowControlPanel);
    }

    /**
     * 设置是否自动显示隐藏操作面板，要设置{@link #setPanelControl(PanelControlListener)}才能生效
     *
     * @param isAuto
     * @return
     */
    public PlayerController setAutoControlPanel(boolean isAuto) {
        if (isAuto) {
            isShowControlPanl = false;
            mAutoControlPanelRunnable = new AutoControlPanelRunnable();
        } else {
            isShowControlPanl = true;
            mAutoControlPanelRunnable = null;
        }
        return this;
    }

    /**
     * 这个方法要调用{@link #setAutoControlPanel(boolean)}，并且设置setAutoControlPanel(true)时才能生效
     * 这里最终执行的回调是{@link PanelControlListener}对应着{@link #setPanelControl(PanelControlListener)}
     *
     * @param views 设置控件按下时移除执行{@link #operatorPanl()}方法，离开控件时执行{@link #operatorPanl()}方法
     * @return
     */
    public PlayerController setAutoControlListener(View... views) {
        for (View view : views) {
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_DOWN:
                            if (mAutoControlPanelRunnable != null) {
                                mAutoControlPanelRunnable.stop();
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            endGesture();
                            break;
                    }
                    return false;
                }
            });
        }
        return this;
    }

    /**
     * 显示或隐藏操作面板
     */
    public PlayerController operatorPanl() {
        isShowControlPanl = !isShowControlPanl;
        if (isShowControlPanl) {
            mHandler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.start(AutoControlPanelRunnable.AUTO_INTERVAL);
            }
        } else {
            mHandler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.stop();
            }
        }
        if (panelControlListener != null) {
            panelControlListener.operatorPanel(isShowControlPanl);
        }
        return this;
    }

    /**
     * 是否仅仅为全屏
     */
    public PlayerController setOnlyFullScreen(boolean isFull) {
        this.isOnlyFullScreen = isFull;
        tryFullScreen(isOnlyFullScreen);
        if (isOnlyFullScreen) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
        return this;
    }

    /**
     * 设置是否禁止双击
     */
    public PlayerController setForbidDoulbeUp(boolean flag) {
        this.isForbidDoulbeUp = flag;
        return this;
    }

    /**
     * 设置是否禁止隐藏bar
     */
    public PlayerController setForbidHideControlPanl(boolean flag) {
        this.isForbidHideControlPanl = flag;
        return this;
    }

    /**
     * 是否禁止触摸
     */
    public PlayerController forbidTouch(boolean forbidTouch) {
        this.isForbidTouch = forbidTouch;
        return this;
    }

    /**
     * 横竖屏切换
     */
    public PlayerController toggleScreenOrientation() {
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {// 横屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);// 竖屏
        } else if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {//竖屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        return this;
    }

    /**
     * 设置是否开启屏幕常亮   true：保持常亮   false：清除常亮
     *
     * @param isKeep
     * @return
     */
    public PlayerController setKeepScreenOn(boolean isKeep) {
        if (isKeep) {
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        return this;
    }

    /**
     * 当前播放的是否是直播
     */
    public boolean isLive() {
        if (currentUrl != null
                && (currentUrl.startsWith("rtmp://")
                || (currentUrl.startsWith("http://") && currentUrl.endsWith(".m3u8"))
                || (currentUrl.startsWith("http://") && currentUrl.endsWith(".flv")))) {
            isLive = true;
        } else {
            isLive = false;
        }
        return isLive;
    }

    /**
     * 获取界面方向
     */
    private int getScreenOrientation() {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    /**
     * 时长格式化显示
     */
    public static String generateTime(long time) {
        int totalSeconds = (int) (time / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 下载速度格式化显示
     */
    public static String getFormatSize(int size) {
        long fileSize = (long) size;
        String showSize = "";
        if (fileSize >= 0 && fileSize < 1024) {
            showSize = fileSize + "Kb/s";
        } else if (fileSize >= 1024 && fileSize < (1024 * 1024)) {
            showSize = Long.toString(fileSize / 1024) + "KB/s";
        } else if (fileSize >= (1024 * 1024) && fileSize < (1024 * 1024 * 1024)) {
            showSize = Long.toString(fileSize / (1024 * 1024)) + "MB/s";
        }
        return showSize;
    }

    /**
     * ==========================================内部方法=============================
     */

    /**
     * 界面方向改变是刷新界面
     */
    private void doOnConfigurationChanged(final boolean portrait) {
        if (videoView != null && !isOnlyFullScreen) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    tryFullScreen(!portrait);
                }
            });
            orientationEventListener.enable();
        }
    }

    /**
     * 设置界面方向
     */
    private void setFullScreen(boolean fullScreen) {
        if (mActivity != null) {
            WindowManager.LayoutParams attrs = mActivity.getWindow().getAttributes();
            if (fullScreen) {
                attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                mActivity.getWindow().setAttributes(attrs);
                mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } else {
                attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
                mActivity.getWindow().setAttributes(attrs);
                mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }

    }

    /**
     * 是否显示ActionBar
     *
     * @param fullScreen
     */
    private void showActionBar(boolean fullScreen) {
        if (mActivity instanceof AppCompatActivity) {
            ActionBar supportActionBar = ((AppCompatActivity) mActivity).getSupportActionBar();
            if (supportActionBar != null) {
                if (fullScreen) {
                    supportActionBar.hide();
                } else {
                    supportActionBar.show();
                }
            }
        }
    }

    /**
     * 设置界面方向带隐藏actionbar
     */
    private void tryFullScreen(boolean fullScreen) {
        showActionBar(fullScreen);
        setFullScreen(fullScreen);
    }

    /**
     * 手势结束
     */
    private void endGesture() {
        volume = -1;
        brightness = -1f;
        if (newPosition >= 0) {
            mHandler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
            mHandler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
        } else {
            /**什么都不做(do nothing)*/
        }
        mHandler.removeMessages(MESSAGE_HIDE_CENTER_BOX);
        mHandler.sendEmptyMessageDelayed(MESSAGE_HIDE_CENTER_BOX, 500);
        if (mGestureListener != null) {
            mGestureListener.endGesture();
        }
        if (mAutoControlPanelRunnable != null) {
            mAutoControlPanelRunnable.start(AutoControlPanelRunnable.AUTO_INTERVAL);
        }
    }

    /**
     * 同步进度
     */
    private long syncProgress() {
        if (isDragging) {
            return 0;
        }
        if (videoView == null) {
            return 0;
        }
        long position = videoView.getCurrentPosition();
        long duration = videoView.getDuration();
        if (videoController != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                videoController.setProgress((int) pos);
            }
            int percent = videoView.getBufferPercentage();
            videoController.setSecondaryProgress(percent * 10);
        }

        if (isCharge && maxPlaytime + 1000 < getCurrentPosition()) {
            pausePlay();
        } else {
            if (syncProgressListener != null) {
                syncProgressListener.syncTime(position, duration);
            }
        }
        return position;
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {
        if (audioManager != null) {
            if (volume == -1) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (volume < 0)
                    volume = 0;
            }
            int index = (int) (percent * mMaxVolume) + volume;
            if (index > mMaxVolume)
                index = mMaxVolume;
            else if (index < 0)
                index = 0;

            // 变更声音
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

            // 变更进度条
            int i = (int) (index * 1.0 / mMaxVolume * 100);
            if (mGestureListener != null) {
                mGestureListener.onVolumeSlide(i);
            }
        }
    }

    /**
     * 快进或者快退滑动改变进度
     *
     * @param percent
     */
    private void onProgressSlide(float percent) {
        int position = videoView.getCurrentPosition();
        long duration = videoView.getDuration();
        long deltaMax = Math.min(100 * 1000, duration - position);
        long delta = (long) (deltaMax * percent);
        newPosition = delta + position;
        if (newPosition > duration) {
            newPosition = duration;
        } else if (newPosition <= 0) {
            newPosition = 0;
            delta = -position;
        }
        int showDelta = (int) delta / 1000;
        if (mGestureListener != null) {
            mGestureListener.onProgressSlide(newPosition, duration, showDelta);
        }
    }

    /**
     * 亮度滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        if (brightness < 0) {
            brightness = mActivity.getWindow().getAttributes().screenBrightness;
            if (brightness <= 0.00f) {
                brightness = 0.50f;
            } else if (brightness < 0.01f) {
                brightness = 0.01f;
            }
        }
        Log.d(this.getClass().getSimpleName(), "brightness:" + brightness + ",percent:" + percent);
        WindowManager.LayoutParams lpa = mActivity.getWindow().getAttributes();
        lpa.screenBrightness = brightness + percent;
        if (lpa.screenBrightness > 1.0f) {
            lpa.screenBrightness = 1.0f;
        } else if (lpa.screenBrightness < 0.01f) {
            lpa.screenBrightness = 0.01f;
        }
        mActivity.getWindow().setAttributes(lpa);
        if (mGestureListener != null) {
            mGestureListener.onBrightnessSlide(lpa.screenBrightness);
        }
    }

    /**
     * ==========================================内部类=============================
     */

    private class AutoControlPanelRunnable implements Runnable {

        public static final int AUTO_INTERVAL = 5000;

        public void start(long delayMillis) {
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, delayMillis);
        }

        public void stop() {
            mHandler.removeCallbacks(this);
        }

        @Override
        public void run() {
            operatorPanl();
        }
    }

    /**
     * 播放器的手势监听
     */
    private class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
        private boolean FLAG_TRANSLUCENT_STATUS;
        /**
         * 是否是按下的标识，默认为其他动作，true为按下标识，false为其他动作
         */
        private boolean isDownTouch;
        /**
         * 是否声音控制,默认为亮度控制，true为声音控制，false为亮度控制
         */
        private boolean isVolume;
        /**
         * 是否横向滑动，默认为纵向滑动，true为横向滑动，false为纵向滑动
         */
        private boolean isLandscape;

        /**
         * 双击
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            /**视频视窗双击事件*/
            if (!isForbidTouch && !isOnlyFullScreen && !isForbidDoulbeUp) {
                toggleScreenOrientation();
            }
            return true;
        }

        /**
         * 按下
         */
        @Override
        public boolean onDown(MotionEvent e) {
            isDownTouch = true;
            return super.onDown(e);
        }


        /**
         * 滑动
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!isForbidTouch) {
                float mOldX = e1.getX(), mOldY = e1.getY();
                float deltaY = mOldY - e2.getY();
                float deltaX = mOldX - e2.getX();
                if (isDownTouch) {
                    isLandscape = Math.abs(distanceX) >= Math.abs(distanceY);
                    isVolume = mOldX > screenWidthPixels * 0.5f;
                    isDownTouch = false;
                }

                if (isLandscape) {
                    if (!isLive) {
                        /**进度设置*/
                        if (videoView != null) {
                            onProgressSlide(-deltaX / videoView.getWidth());
                        }
                    }
                } else {
                    if (videoView != null) {
                        float percent = deltaY / videoView.getHeight();
                        if (isVolume) {
                            /**声音设置*/
                            onVolumeSlide(percent);
                        } else {
                            /**亮度设置*/
                            onBrightnessSlide(percent);
                        }
                    }
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        /**
         * 单击
         */
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            /**视频视窗单击事件*/
            if (!isForbidTouch) {
                operatorPanl();
            }
            return true;
        }
    }

}
