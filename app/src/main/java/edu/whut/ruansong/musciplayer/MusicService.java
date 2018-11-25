package edu.whut.ruansong.musciplayer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.Random;

/**
 * Created by 阮松 on 2018/11/18.
 * 音乐service，后台处理播放逻辑
 */

public class MusicService extends Service {
    //播放控制命令
    public static final int COMMAND_UNKNOWN = -1;
    public static final int COMMAND_PLAY = 0;
    public static final int COMMAND_PAUSE = 1;
    public static final int COMMAND_STOP = 2;
    public static final int COMMAND_RESUME = 3;
    public static final int COMMAND_PREVIOUS = 4;
    public static final int COMMAND_NEXT = 5;
    public static final int COMMAND_CHECK_IS_PLAYING = 6;
    //    public static final int COMMAND_SEEKTO = 7;
    public static final int PLAYMODE_ORDER = 8;//顺序播放(默认是它)
    public static final int PLAYMODE_LOOP = 9;//单曲循环
    public static final int PLAYMODE_RANDOM = 10;//随机播放
    //播放器状态
    public static final int STATUS_PLAYING = 0;
    public static final int STATUS_PAUSED = 1;
    public static final int STATUS_STOPPED = 2;
    public static final int STATUS_COMPLETED = 3;
    //广播标识
    public static final String BROADCAST_MUSICSERVICE_CONTROL = "MusicService.ACTTION_CONTROL";
    public static final String BROADCAST_MUSICSERVICE_UPDATE_STATUS = "MusicService.ACTTION_UPDATE";

    //歌曲序号，从0开始
    private static int current_number = 0;//当前歌曲序号
    private int next_number = 0;//获取的序号
    private int status;
    private int PlayMode = 1;//1顺序 2单曲循环 3随机播放
    private String path;//歌曲path
    //媒体播放器
    private MediaPlayer player = new MediaPlayer();

    private CommandReceiver receiver;

    public static int getCurrent_number() {
        return current_number;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //绑定广播接收器，可以接受广播
        bindCommandReceiver();
        Log.w("MusicService", "服务启动");
        status = MusicService.STATUS_STOPPED;
        player.setOnCompletionListener(completionListener);//监听播放是否完成
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("MusicService", "onDestroy executed");
        if (player != null) {
            player.release();
        }
        Log.w("MusicService", "命令接收器已经被取消注册，服务被销毁");
        unregisterReceiver(receiver);
    }


    /*内部类，接受广播命令并执行操作*/
    class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //获取命令
            int command = intent.getIntExtra("command", COMMAND_UNKNOWN);
            Log.w("MusicService", "获取到了广播" + "+" + command);
            //执行命令
            switch (command) {
                case COMMAND_PLAY:
                    next_number = intent.getIntExtra("number", 0);//获取点击位置
                    Log.w("MusicService", "获取点击位置" + next_number);
                    play_pause(next_number);
                    break;
                case COMMAND_RESUME:
                    next_number = intent.getIntExtra("number", 0);//获取点击位置
                    Log.w("MusicService", "得到位置" + next_number);
                    resume();
                    break;
                case COMMAND_PREVIOUS:
                    Log.w("MusicService", "上一首");
                    moveNumberToPrevious();
                    break;
                case COMMAND_NEXT:
                    Log.w("MusicService", "下一首");
                    moveNumberToNext();
                    break;
                case COMMAND_PAUSE:
                    Log.w("MusicService", "暂停");
                    pause();
                    break;
                case COMMAND_STOP:
                    Log.w("MusicService", "停止");
                    stop();
                    break;
                case COMMAND_CHECK_IS_PLAYING:
                    if (player != null && player.isPlaying())
                        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
                    else
                        sendBroadcastOnStatusChanged(MusicService.STATUS_STOPPED);
                    break;
                case PLAYMODE_ORDER://顺序播放
                    PlayMode = 1;
                    sendBroadcastOnStatusChanged(MusicService.PLAYMODE_ORDER);
                    break;
                case PLAYMODE_LOOP://单曲循环
                    PlayMode = 2;
                    sendBroadcastOnStatusChanged(MusicService.PLAYMODE_LOOP);
                    break;
                case PLAYMODE_RANDOM://随机播放
                    PlayMode = 3;
                    sendBroadcastOnStatusChanged(MusicService.PLAYMODE_RANDOM);
                    break;
                case COMMAND_UNKNOWN:
                default:
                    break;
            }
        }
    }

    //绑定广播接收器
    private void bindCommandReceiver() {
        receiver = new CommandReceiver();
        IntentFilter filter = new IntentFilter(BROADCAST_MUSICSERVICE_CONTROL);
        registerReceiver(receiver, filter);
    }

    //发送广播提醒状态改变了
    private void sendBroadcastOnStatusChanged(int status) {
        Intent intent = new Intent(BROADCAST_MUSICSERVICE_UPDATE_STATUS);
        intent.putExtra("status", status);
        Log.w("MusicService", "发送状态广播" + status);
        sendBroadcast(intent);
    }

//    private void load(int number) {
//        try {
//            player.reset();
//            player.setDataSource(DisplayActivity.getSongsList().get(number).getSong_addr());
//            Song song = DisplayActivity.getSongsList().get(number);//加入历史记录
//            player.prepare();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        player.setOnCompletionListener(completionListener);
//    }

    MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            sendBroadcastOnStatusChanged(MusicService.STATUS_COMPLETED);
            Log.w("DisplayActivity", "已经播放完毕，播放下一首！");
            moveNumberToNext();
        }
    };

    private void play_pause(int number) {
        if (player != null && player.isPlaying()) {
            if (next_number == current_number) {//点击的正在播放的歌曲
                pause();
                Log.w("MusicService", "点击的正在播放的歌曲，暂停播放");
            } else {//点击了新歌曲
                Song song = DisplayActivity.getSongsList().get(next_number);
                path = song.getSong_addr();//获取其地址
                PlayHistory.addSong(song);
                player.stop();
                prePlay(path);//准备播放
                Log.w("MusicService", "准备播放");
                status = MusicService.STATUS_PLAYING;
                sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
                current_number = next_number;
            }
        } else {//没有在播放歌曲，直接播放
            Log.w("MusicService", "没有在播放歌曲，直接播放" + next_number);
            Song song = DisplayActivity.getSongsList().get(next_number);
            path = song.getSong_addr();//获取其地址
            PlayHistory.addSong(song);
            prePlay(path);
            status = MusicService.STATUS_PLAYING;
            sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
            current_number = next_number;
        }
    }

    public void prePlay(String str) {
        try {
            player.reset();
            player.setDataSource(path);
            player.prepare();
            player.start();//开始播放
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play() {
        Song song = DisplayActivity.getSongsList().get(next_number);
        path = song.getSong_addr();//获取其地址
        PlayHistory.addSong(song);
        prePlay(path);
        status = MusicService.STATUS_PLAYING;
        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
        current_number = next_number;
    }

    private void moveNumberToPrevious() {
        //判断是否到达顶端
        if (next_number == 0) {
            Toast.makeText(MusicService.this, "已经达到了列表顶部!", Toast.LENGTH_SHORT).show();
        } else {
            --next_number;
            play_pause(next_number);
        }
    }

    private void moveNumberToNext() {
        switch (PlayMode) {
            case 1:
                next_number = current_number + 1;//默认顺序播放
                break;
            case 2:
                next_number = current_number;//单曲循环
                sendBroadcastOnStatusChanged(PLAYMODE_LOOP);
                break;
            case 3://随机播放
                Random random = new Random();
                //调试用
                Log.w("MusicService", "moveNumberToNext() DisplayActivity.getSongsList().size()是"
                        + DisplayActivity.getSong_number());
                next_number = random.nextInt(DisplayActivity.getSong_number());
                break;
            default:
                break;
        }
        Log.w("MusicService", "moveNumberToNext() next_number是" + next_number);
        //判断是否到达了底端
        if (next_number == (DisplayActivity.getSongsList().size() - 1)) {
            Toast.makeText(MusicService.this, "已经达到了列表底部!", Toast.LENGTH_SHORT).show();
            next_number--;
            play();
        }
        play();
    }

    private void pause() {
        if (player.isPlaying()) {
            player.pause();
            status = MusicService.STATUS_PAUSED;
            sendBroadcastOnStatusChanged(MusicService.STATUS_PAUSED);
        }
    }

    private void stop() {
        if (player.isPlaying()) {
            player.stop();
            status = MusicService.STATUS_STOPPED;
            sendBroadcastOnStatusChanged(MusicService.STATUS_STOPPED);
        }
    }

    private void resume() {//暂停后的恢复播放
        player.start();
        status = MusicService.STATUS_PLAYING;
        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
    }

    private void replay() {//播放完成后的重新播放
        player.start();
        status = MusicService.STATUS_PLAYING;
        sendBroadcastOnStatusChanged(MusicService.STATUS_PLAYING);
    }
}