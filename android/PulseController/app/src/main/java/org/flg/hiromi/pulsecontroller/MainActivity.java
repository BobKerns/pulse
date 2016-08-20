package org.flg.hiromi.pulsecontroller;

import android.animation.Animator;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActionBarActivity {
    private TextView text_view;

    private IPulseCommChannel commChannel;
    private IUDPMessageContext msgContext;

    private SharedPreferences prefs;

    private ServiceConnection msgContextConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            msgContext = (IUDPMessageContext)service;
            // Now that we have have our DB connected, fire up the UI, etc.
            Intent intent = new Intent(MainActivity.this, PulseCommService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            msgContext = null;
        }
    };

    //  connect to our background communication service.
    private ServiceConnection serviceConnection = new ServiceConnection() {
        private String getStatus(JSONObject status) {
            if (status == null) {
                // We get null status on connection failures.
                return null;
            }
            try {
                return status.getString("status");
            } catch (JSONException e) {
                return "OK";
            }
        }
        /**
         * Once the service is connected, we hook up the uI
         * @param name
         * @param service
         */
        @Override
        public void onServiceConnected(final ComponentName name, IBinder service) {
            commChannel = (IPulseCommChannel)service;
            commChannel.registerErrorWatcher(new IPulseCommChannel.ErrWatcher() {
                @Override
                public void onError(Throwable t) {
                    onServiceError(name.flattenToShortString(), t);
                }
            });
            final int colorOK = getResources().getColor(R.color.colorOK);
            final int colorDisconnected = getResources().getColor(R.color.colorDisconnected);
            commChannel.registerStatusWatcher(new IPulseCommChannel.StatusWatcher() {
                @Override
                public void onStatus(JSONObject status) {
                    String stat = getStatus(status);
                    TextView text = (TextView)MainActivity. this.findViewById(R.id.textView_connect);
                    if (status != null) {
                        text.setText("Connected " + stat);
                        text.setTextColor(colorOK);
                    } else {
                        text.setText("Disconnected");
                        text.setTextColor(colorDisconnected);
                    }
                }
            });
            View v = findViewById(R.id.nandesuka);
            initControls(v);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            commChannel = null;
        }
    };

    private void onServiceError(String name, Throwable t) {
        String msg = t.getMessage();
        msg = t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
        // Toast.makeText(this, "Error in REST service: " + msg, Toast.LENGTH_LONG).show();
        text_view.setText("Error: " + msg);
    }

    private HeartbeatService.Channel beatChannel = null;
    private ServiceConnection pulseServiceConnection = new ServiceConnection() {
        // Map the podID to the right icon.
        private ImageView chooseIcon(int podId) {
            ImageView pulse_icon_1 = (ImageView)findViewById(R.id.pulse_icon_1);
            ImageView pulse_icon_2 = (ImageView)findViewById(R.id.pulse_icon_2);
            ImageView pulse_icon_3 = (ImageView)findViewById(R.id.pulse_icon_3);
            ImageView pulse_icon_4 = (ImageView)findViewById(R.id.pulse_icon_4);
            ImageView pulse_icon_5 = (ImageView)findViewById(R.id.pulse_icon_5);
            switch (podId % 5) {
                case 0: return pulse_icon_5;
                case 1: return pulse_icon_4;
                case 2: return pulse_icon_3;
                case 3: return pulse_icon_2;
                case 4: return pulse_icon_1;
            }
            return null;
        }
        @Override
        public void onServiceConnected(final ComponentName name, IBinder service) {
            beatChannel = (HeartbeatService.Channel)service;
            final float iy = chooseIcon(0).getY();
            ((HeartbeatService.Channel) service).registerListener(new HeartbeatService.HeartbeatListener() {
                @Override
                public void onBeat(Pulse pulse) {
                    final ImageView icon = chooseIcon(pulse.getPod());
                    if (icon != null) {
                        try {
                            icon.animate()
                                    .setDuration(50)
                                    .y(iy + 7)
                                    .scaleX(.5f)
                                    .scaleY(.5f)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            icon.animate()
                                                    .setDuration(270)
                                                    .y(iy)
                                                    .scaleX(1)
                                                    .scaleY(1);
                                        }
                                    });
                        } catch (Error | Exception e) {
                            Toast.makeText(MainActivity.this, "Error: " + e, Toast.LENGTH_LONG).show();
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    onServiceError(name.flattenToShortString(), t);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            beatChannel.unregisterListeners();
        }
    };

    private void initControls(View v) {
        if (v instanceof Button) {
            if (v.getTag() instanceof String) {
                initButton((Button)v);
            }
        } else if (v instanceof SeekBar) {
            if (v.getTag() instanceof String) {
                initSeekbar((SeekBar) v);
            }
        } else if (v instanceof Spinner) {
            if (v.getTag() instanceof String) {
                initSpinner((Spinner)v);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup p = (ViewGroup)v;
            for (int i = 0; i < p.getChildCount(); i++) {
                initControls(p.getChildAt(i));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startServices();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_main);
        text_view = (TextView)findViewById(R.id.textView_status);
    }

    @Override
    protected void onPause() {
        stopServices();
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void startServices() {
        if (msgContext == null) {
            Intent intent = new Intent(this, UDPMessageDataService.class);
            bindService(intent, msgContextConn, Context.BIND_AUTO_CREATE);
        }
        if (beatChannel == null) {
            Intent intent = new Intent(this, HeartbeatService.class);
            bindService(intent, pulseServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);
        text_view = (TextView)findViewById(R.id.textView_status);
        View v = findViewById(R.id.nandesuka);
        initControls(v);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void stopServices() {
        unbindService(serviceConnection);
        unbindService(pulseServiceConnection);
        unbindService(msgContextConn);
    }

    private void resetButton(Button btnA) {
        // Set the button background back
        Drawable bg = (Drawable) btnA.getTag(R.id.button_background);
        if (bg != null) {
            btnA.setBackground(bg);
            btnA.setEnabled(true);
        }
    }


    /**
     * Initialize a button. The button must have a tag field with the name of the event to send.
     * @param btnA
     */
    public void initButton(final Button btnA)
    {
        final Object tagv = (String)btnA.getTag();
        final String tag = (tagv instanceof String) ? (String)tagv : null;
        commChannel.watchEvent((String) tag, new IPulseCommChannel.IntWatcher() {
            @Override
            public void onChange(String name, int val, boolean update) {
                String state = (val == 0) ? "Failed" : "OK";
                text_view.setText(tag + ": " + state);
                resetButton(btnA);
            }

            @Override
            public void onError(String name, Throwable t) {
                onServiceError(name, t);
                // Set the button background back
                Drawable bg = (Drawable)btnA.getTag(R.id.button_background);
                resetButton(btnA);
            }
        });
        btnA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg)
            {
                if (commChannel != null) {
                    text_view.setText(tag + ": ");
                    commChannel.trigger(tag);
                    Drawable bg = btnA.getBackground();
                    btnA.setTag(R.id.button_background, bg);
                    // Set the button color to show it's currently being processed
                    btnA.setBackgroundColor(Color.BLUE);
                    btnA.setEnabled(false);
                }
            }

        });
        String label = msgContext.getLabel(tag);
        if (label != null) {
            btnA.setText(label);
        }
    }

    public void initSpinner(final Spinner spinner) {
        commChannel.watchEvent((String) spinner.getTag(), new IPulseCommChannel.IntWatcher() {
            @Override
            public void onChange(String name, int val, boolean update) {
                Object item = spinner.getItemAtPosition(val);
                text_view.setText(name + ": " + item);
                if (!update) {
                    spinner.setSelection(val);
                }
            }

            @Override
            public void onError(String name, Throwable t) {
                onServiceError(name, t);
            }
        });
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String tag = (String)spinner.getTag();
                text_view.setText(tag + ": ");
                if (commChannel != null) {
                    commChannel.setIntParam(tag, position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    /**
     * Initialize a seek bar. The seek bar must have a tag field with the name of the parameter to set.
     * @param seek_bar
     */
    public void initSeekbar(final SeekBar seek_bar) {
        text_view.setText(seek_bar.getTag() + ": " + seek_bar.getProgress() + " / " + seek_bar.getMax());
        commChannel.watchParameter((String)seek_bar.getTag(), new IPulseCommChannel.IntWatcher() {
            @Override
            public void onChange(String name, int val, boolean update) {
                text_view.setText(seek_bar.getTag() + " : " + val + " / " + seek_bar.getMax());
                if (!update) {
                    seek_bar.setProgress(val);
                }
            }

            @Override
            public void onError(String name, Throwable t) {
                onServiceError(name, t);
            }
        });
        seek_bar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        int progress= seekBar.getProgress();
                        if (commChannel != null) {
                            commChannel.setIntParam((String)seekBar.getTag(), progress);
                        }
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent().setClass(getApplicationContext(), SettingsActivity.class));
                return true;
            case R.id.udp_messages:
                startActivity(new Intent().setClass(getApplicationContext(), UDPMessageListActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
