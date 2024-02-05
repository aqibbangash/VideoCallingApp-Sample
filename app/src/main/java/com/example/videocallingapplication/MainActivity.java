package com.example.videocallingapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.example.videocallingapplication.databinding.ActivityMainBinding;

import java.util.Random;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;

    int otherUserJoined = 0;

    // Fill the App ID of your project generated on Agora Console.
    private final String appId = "2b16ecb30f364a99bf249fa6ebb1e777";
    // Fill the channel name.
    private String channelName = "Default";
    // Fill the temp token generated on Agora Console.
    private String token = "";
    // An integer that identifies the local user.
    private int uid = 0;
    private boolean isJoined = false;

    private RtcEngine agoraEngine = null;

    private SurfaceView localSurfaceView = null;
    private SurfaceView remoteSurfaceView = null;

    AlertDialog.Builder builder = null;

    private static final int PERMISSION_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS =
            {
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            };

    private boolean checkSelfPermission() {
        return ContextCompat.checkSelfPermission(this, REQUESTED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, REQUESTED_PERMISSIONS[1]) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupVideoSDKEngine() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            agoraEngine = RtcEngine.create(config);
            agoraEngine.enableVideo();
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    void setJoined(Boolean joined) {
        this.isJoined = joined;

//        runOnUiThread(() -> {
//            if (isJoined) {
//                binding.leaveButton.setVisibility(View.VISIBLE);
//                binding.joinOtherChannel.setVisibility(View.GONE);
//                binding.joinMyChannel.setVisibility(View.GONE);
//            } else {
//                binding.leaveButton.setVisibility(View.GONE);
//                binding.joinOtherChannel.setVisibility(View.VISIBLE);
//                binding.joinMyChannel.setVisibility(View.VISIBLE);
//            }
//        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        generateChannel();

        if (!checkSelfPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_ID);
        }
        setupVideoSDKEngine();

        binding.joinMyChannel.setOnClickListener(v -> {

//            runOnUiThread(() -> setupRemoteVideo(otherUserJoined));
            joinCall(channelName);


        });

        binding.joinOtherChannel.setOnClickListener(v -> initDialog());

        binding.leaveButton.setOnClickListener(v -> leaveCall());

        binding.tvChannelId.setText("Your Channel: " + channelName);

//        joinMyChannel();

    }

    private void initDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join Channel");

        // Create the EditText for number input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        input.setHint("Channel Number");

        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Join", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Check if the input is a valid number
                try {
                    int number = Integer.parseInt(input.getText().toString());
                    joinCall(Integer.toString(number));
                } catch (NumberFormatException e) {
                    // Handle invalid input (not a number)
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void generateChannel() {

        if (this.getSharedPreferences("MyPrefs", 0).getString("channelId", "0").equals("0")) {
            Random r = new Random( System.currentTimeMillis() );
            int c = 10000 + r.nextInt(20000);
            channelName = Integer.toString(c);
//            this.getSharedPreferences("MyPrefs", 0).edit().putString("channelId", channelName).commit();
        } else {
            channelName = this.getSharedPreferences("MyPrefs", 0).getString("channelId", "0");
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        agoraEngine.stopPreview();
        agoraEngine.leaveChannel();

        // Destroy the engine in a sub-thread to avoid congestion
        new Thread(() -> {
            RtcEngine.destroy();
            agoraEngine = null;
        }).start();
    }


    private void leaveCall() {

        if (!isJoined) {
            Toast.makeText(this, "Join a channel first", Toast.LENGTH_SHORT).show();
        } else {
            agoraEngine.leaveChannel();
            Toast.makeText(this, "You left the channel", Toast.LENGTH_SHORT).show();
            // Stop remote video rendering.
            if (remoteSurfaceView != null) remoteSurfaceView.setVisibility(View.GONE);
            // Stop local video rendering.
            if (localSurfaceView != null) localSurfaceView.setVisibility(View.GONE);
            setJoined(false);
        }

    }

    private void joinCall(String channel) {

        if (checkSelfPermission()) {
            ChannelMediaOptions options = new ChannelMediaOptions();

            // For a Video call, set the channel profile as COMMUNICATION.
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            // Set the client role as BROADCASTER or AUDIENCE according to the scenario.
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            // Display LocalSurfaceView.
            setupLocalVideo();
            localSurfaceView.setVisibility(View.VISIBLE);
            // Start local preview.
            agoraEngine.startPreview();
            // Join the channel with a temp token.
            // You need to specify the user ID yourself, and ensure that it is unique in the channel.
            agoraEngine.joinChannel(token, channel, uid, options);
        } else {
            Toast.makeText(getApplicationContext(), "Permissions was not granted", Toast.LENGTH_SHORT).show();
        }

    }

    private void joinMyChannel() {

        if (checkSelfPermission()) {
            ChannelMediaOptions options = new ChannelMediaOptions();

            // For a Video call, set the channel profile as COMMUNICATION.
//            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
            // Set the client role as BROADCASTER or AUDIENCE according to the scenario.
//            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            // Display LocalSurfaceView.
//            setupLocalVideo();
//            localSurfaceView.setVisibility(View.VISIBLE);
            // Start local preview.
//            agoraEngine.startPreview();
            // Join the channel with a temp token.
            // You need to specify the user ID yourself, and ensure that it is unique in the channel.
            agoraEngine.joinChannel(token, channelName, uid, options);
        } else {
            Toast.makeText(getApplicationContext(), "Permissions was not granted", Toast.LENGTH_SHORT).show();
        }

    }




    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        // Listen for the remote host joining the channel to get the uid of the host.
        public void onUserJoined(int uid, int elapsed) {
//            runOnUiThread(() ->  Toast.makeText(MainActivity.this, "Remote user joined " + uid, Toast.LENGTH_SHORT).show());
            showMessage("Remote user joined " + uid);
            // Set the remote video view
            otherUserJoined = uid;
//            runOnUiThread(() -> binding.joinMyChannel.setVisibility(View.VISIBLE));
            runOnUiThread(() -> setupRemoteVideo(uid));
            setJoined(true);
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            setJoined(true);
            runOnUiThread(() ->  Toast.makeText(MainActivity.this, "Joined Channel " + channel, Toast.LENGTH_SHORT).show());
            showMessage("Joined Channel " + channel);
            if (channel.equals(channelName)) {
                runOnUiThread(() ->  binding.tvChannelId.setText("Online on Channel: " + channelName));
            }
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            showMessage("Remote user offline " + uid + " " + reason);
          //  Toast.makeText(MainActivity.this, "Remote user offline " + uid + " " + reason, Toast.LENGTH_SHORT).show();
            runOnUiThread(() -> remoteSurfaceView.setVisibility(View.GONE));
        }
    };

    private void setupRemoteVideo(int uid) {
      //  FrameLayout container = findViewById(R.id.remote_video_view_container);
        remoteSurfaceView = new SurfaceView(getBaseContext());
        remoteSurfaceView.setZOrderMediaOverlay(true);
        binding.remoteUser.addView(remoteSurfaceView);
        agoraEngine.setupRemoteVideo(new VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_FIT, uid));
        // Display RemoteSurfaceView.
        remoteSurfaceView.setVisibility(View.VISIBLE);
    }
    private void setupLocalVideo() {
     //   FrameLayout container = findViewById(R.id.local_video_view_container);
        // Create a SurfaceView object and add it as a child to the FrameLayout.
        localSurfaceView = new SurfaceView(getBaseContext());
        binding.localUser.addView(localSurfaceView);
        binding.localUser.bringToFront();
        // Pass the SurfaceView object to Agora so that it renders the local video.
        agoraEngine.setupLocalVideo(new VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));


    }


}