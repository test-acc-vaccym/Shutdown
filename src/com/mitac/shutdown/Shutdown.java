package com.mitac.shutdown;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;

import java.lang.reflect.Method;
import android.os.IBinder;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
//import android.os.IPowerManager;
//import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.ArrayAdapter;
import android.view.View.OnKeyListener;
import android.view.KeyEvent;

import android.os.SystemClock;
import android.os.PowerManager;
import android.app.AlarmManager;

import android.os.SystemProperties;

public class Shutdown extends Activity {

    // public static final String ACTION_REBOOT = "android.intent.action.REBOOT";
    // public static final String ACTION_REQUEST_SHUTDOWN = "android.intent.action.ACTION_REQUEST_SHUTDOWN";
    public static final String PROPERTY_ACTION_DURATION = "persist.sys.action.duration";
    public static final String PROPERTY_ACTION_SUSPEND = "persist.sys.action.suspend";
    public static final String PROPERTY_ACTION_COUNTER = "persist.sys.action.counter";
    public static final String PROPERTY_ACTION_TOTAL = "persist.sys.action.total";
    public static final String PROPERTY_ACTION_OPTION = "persist.sys.action.option";
    public static final String PROPERTY_PING_SWITCH = "persist.sys.ping.switch";
    public static final String PROPERTY_PING_FAIL = "persist.sys.ping.fail";
    private static final String ACTION_ALARM = "mitac.alarm";

    private int suspendTime;
    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;

    private KeyguardManager keyguardManager;
    private String lockTag;
    private KeyguardManager.KeyguardLock keyguardLock;

    private static String TAG = "Shutdown";
    private TextView mTextView01;
    private TextView mPingFail;
    private TextView mPingTip;
    private TextView mCounter;
    private TextView mSuspendTip;
    private EditText mEditTotal;
    private Spinner spinner;
    private Spinner spinner_suspend;
    private RadioGroup mRadioGroupAPI = null;
    private RadioButton mRadioButtonAPI1 = null;
    private Button mButtonStart;
    private Switch mSwitch;
    private boolean mPingSwitch = false;
    private static final int MSG_AUTO_REFRESH = 0x1000;
    private int duration;
    private int pos;
    private int counter;
    private int ping_fail;
    private int total;
    private int option;

    private IntentFilter mIntentFilter;
    private int mBatteryLevel;
    private int mPlugged;

    private String ping_result;

    public void wakeup() {
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        @SuppressWarnings("deprecation")
        PowerManager.WakeLock mWakelock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Bright");
        mWakelock.acquire();
        mWakelock.release();
    }

    public void onActionThread() {
//        Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
//        intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(intent);

        if(option == 0) {
            Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
            intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if(option == 1) {
            //reboot the device after a while
            Intent intent=new Intent(Intent.ACTION_REBOOT);
            intent.putExtra("nowait", 1);
            intent.putExtra("interval", 1);
            intent.putExtra("window", 0);
            sendBroadcast(intent);
        } else if(option == 2) {
            // Set alarm as a wakeup source
            //cancel the alarm
            //unregisterAlarm();
            registerAlarm();
            // Go to sleep here
            PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
            pm.goToSleep(SystemClock.uptimeMillis());
        } else if(option == 3) {
            // Go to sleep here
            PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
            pm.goToSleep(SystemClock.uptimeMillis());
            wakeup();

            //Prepare next cycle
            mCounter.setText("Counter: "+counter);
            String time = SystemProperties.get(PROPERTY_ACTION_DURATION, "60");
            duration = Integer.valueOf(time).intValue();
            mTextView01.setText(" "+duration);
            mHandler.removeCallbacks(runnable);
            if(mHandler!=null && total>counter) {
                 mHandler.postDelayed(runnable, 1000);
            }
        }

    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                mBatteryLevel = intent.getIntExtra("level", 0);
                mPlugged = intent.getIntExtra("plugged", 0);
                Log.d(TAG, "level: "+ mBatteryLevel + " mPlugged: "+ mPlugged);
                if (mPlugged != BatteryManager.BATTERY_PLUGGED_AC) {
                    //onActionThread();
                }
            } else if(action.equals(Intent.ACTION_SCREEN_OFF)) {
                // do nothing
                Log.d(TAG, "ACTION_SCREEN_OFF");
            } else if(action.equals(Intent.ACTION_SCREEN_ON)) {
                // do nothing
                Log.d(TAG, "ACTION_SCREEN_ON");
            } else if(action.equals(ACTION_ALARM)) {
                Log.d(TAG, "ACTION_ALARM");
                //enlight the screen
                wakeup();
                //Prepare next cycle
                mCounter.setText("Counter: "+counter);
                String time = SystemProperties.get(PROPERTY_ACTION_DURATION, "60");
                duration = Integer.valueOf(time).intValue();
                mTextView01.setText(" "+duration);
                if(mHandler!=null && total>counter) {
                    mHandler.postDelayed(runnable, 1000);
                 }
            }
        }
    };

    public void registerAlarm() {
        if (alarmManager == null || suspendTime == 0 || pendingIntent == null) {
            return;
        }
        //XXX: only for PAVO, RTC only triggers the device at the time level(minute)
        String product = SystemProperties.get("ro.product.name", "");
        if(product.equals("pavo")) {
            long init_sec = System.currentTimeMillis()/1000;
            long next_sec = init_sec+suspendTime;
            long init_min = init_sec/60;
            long next_min = next_sec/60;
            Log.d(TAG, "sec: "+init_sec+", "+next_sec);
            if(next_min == init_min) {
                next_min += 1;
            } else if((next_min*60) < (10+init_sec)) {
                next_min += 1;
            }
            Log.d(TAG, "min: "+init_min+", "+next_min);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy:MM:dd kk:mm:ss");
            Date date = new Date((next_min*60)*1000);
            Log.d(TAG, "Wake up time: "+formatter.format(date));

            alarmManager.set(AlarmManager.RTC_WAKEUP, (next_min*60)*1000, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + suspendTime*1000, pendingIntent);
        }
    }

    public void unregisterAlarm() {
        if (alarmManager == null || pendingIntent == null) {
            return;
        }
        alarmManager.cancel(pendingIntent);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.main);
        mTextView01 = (TextView) findViewById(R.id.myTextView1);
        mButtonStart = (Button) findViewById(R.id.start_test);
        mCounter = (TextView) findViewById(R.id.counter);
        mPingFail = (TextView) findViewById(R.id.ping_fail);
        mPingTip = (TextView)findViewById(R.id.tv_ping);

        String strOption = SystemProperties.get(PROPERTY_ACTION_OPTION, "0");
        option = Integer.valueOf(strOption).intValue();

        String time = SystemProperties.get(PROPERTY_ACTION_DURATION, "60");
        duration = Integer.valueOf(time).intValue();
        mTextView01.setText(" "+duration);

        spinner = (Spinner) findViewById(R.id.spinner_duration);
        String[] curs = getResources().getStringArray(R.array.spinner_duration);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.myspinner, curs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);


        if(duration == 90) pos = 0;
        else if(duration == 60) pos = 1;
        else if(duration == 30) pos = 2;
        else if(duration == 10) pos = 3;

        spinner.setSelection(pos,true);
        spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub
                if(position == 0) {
                    duration = 90;
                } else if(position == 1) {
                    duration = 60;
                } else if(position == 2) {
                    duration = 30;
                } else if(position == 3) {
                    duration = 10;
                }
                //TextView tv = (TextView)view;
                //tv.setTextColor(getResources().getColor(android.R.color.white));
                //tv.setTextSize(12.0f);
                //tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

                mTextView01.setText(" "+duration);
                SystemProperties.set(PROPERTY_ACTION_DURATION, Integer.toString(duration));
                //Toast.makeText(Shutdown.this, "onItemSelected", Toast.LENGTH_SHORT).show();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });

        mSuspendTip = (TextView) findViewById(R.id.suspend);
        String suspend = SystemProperties.get(PROPERTY_ACTION_SUSPEND, "60");
        suspendTime = Integer.valueOf(suspend).intValue();
        spinner_suspend = (Spinner) findViewById(R.id.spinner_suspend);
        String[] curs_suspend = getResources().getStringArray(R.array.spinner_suspend);
        ArrayAdapter<String> adapter_suspend = new ArrayAdapter<String>(this, R.layout.myspinner, curs_suspend);
        adapter_suspend.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_suspend.setAdapter(adapter_suspend);

        if(suspendTime == 90) pos = 0;
        else if(suspendTime == 60) pos = 1;
        else if(suspendTime == 30) pos = 2;
        else if(suspendTime == 10) pos = 3;

        spinner_suspend.setSelection(pos,true);
        if(option == 0 || option == 1 || option == 3) {
            mSuspendTip.setVisibility(View.GONE);
            spinner_suspend.setVisibility(View.GONE);
        }
        spinner_suspend.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub
                if(position == 0) {
                    suspendTime = 90;
                } else if(position == 1) {
                    suspendTime = 60;
                } else if(position == 2) {
                    suspendTime = 30;
                } else if(position == 3) {
                    suspendTime = 10;
                }

                SystemProperties.set(PROPERTY_ACTION_SUSPEND, Integer.toString(suspendTime));
                //Toast.makeText(Shutdown.this, "onItemSelected", Toast.LENGTH_SHORT).show();
            }
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });


        mRadioGroupAPI = (RadioGroup) findViewById(R.id.radio_group_api);
        
        int RADIO_ID = 0;
        if(option==0) RADIO_ID = R.id.radio_shutdown;
        else if(option == 1) RADIO_ID = R.id.radio_reboot;
        else if(option == 2) RADIO_ID = R.id.option_suspend;
        else if(option == 3) RADIO_ID = R.id.option_screen;
        mRadioButtonAPI1 = (RadioButton) findViewById(RADIO_ID);
        mRadioButtonAPI1.toggle();
        mRadioGroupAPI.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {              
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if(checkedId==R.id.radio_shutdown){
                    option = 0;
                    Toast.makeText(Shutdown.this, "shut down", Toast.LENGTH_SHORT).show();
                    SystemProperties.set(PROPERTY_ACTION_OPTION, Integer.toString(option));
                    mSuspendTip.setVisibility(View.GONE);
                    spinner_suspend.setVisibility(View.GONE);
                }  else if(checkedId==R.id.radio_reboot){
                    option = 1;
                    Toast.makeText(Shutdown.this, "reboot", Toast.LENGTH_SHORT).show();
                    SystemProperties.set(PROPERTY_ACTION_OPTION, Integer.toString(option));
                    mSuspendTip.setVisibility(View.GONE);
                    spinner_suspend.setVisibility(View.GONE);
                } else if(checkedId == R.id.option_suspend) {
                    option = 2;
                    Toast.makeText(Shutdown.this, "suspend", Toast.LENGTH_SHORT).show();
                    SystemProperties.set(PROPERTY_ACTION_OPTION, Integer.toString(option));
                    mSuspendTip.setVisibility(View.VISIBLE);
                    spinner_suspend.setVisibility(View.VISIBLE);
                } else if(checkedId == R.id.option_screen) {
                    option = 3;
                    Toast.makeText(Shutdown.this, "screen", Toast.LENGTH_SHORT).show();
                    SystemProperties.set(PROPERTY_ACTION_OPTION, Integer.toString(option));
                    mSuspendTip.setVisibility(View.GONE);
                    spinner_suspend.setVisibility(View.GONE);
                }
            }
        });
        
        String strTotal = SystemProperties.get(PROPERTY_ACTION_TOTAL, "0");
        total = Integer.valueOf(strTotal).intValue();
        String strCount = SystemProperties.get(PROPERTY_ACTION_COUNTER, "0");
        counter = Integer.valueOf(strCount).intValue();
        mCounter.setText("Counter: "+counter);

        String strPing = SystemProperties.get(PROPERTY_PING_FAIL, "0");
        ping_fail = Integer.valueOf(strPing).intValue();
        mPingFail.setText("Fail to ping: "+ping_fail);

        mPingSwitch = SystemProperties.getBoolean(PROPERTY_PING_SWITCH, false);

        mSwitch = (Switch) findViewById(R.id.switch_ping);
        mSwitch.setChecked(mPingSwitch);
        if(mPingSwitch == false) {
            mPingFail.setVisibility(View.GONE);
        }

        //Hide the fucntion(ping) here
        mPingTip.setVisibility(View.GONE);
        mSwitch.setVisibility(View.GONE);

        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    mPingSwitch = true;
                    mPingFail.setVisibility(View.VISIBLE);
                    SystemProperties.set(PROPERTY_PING_SWITCH, "1");
                }else {
                    mPingSwitch = false;
                    mPingFail.setVisibility(View.GONE);
                    SystemProperties.set(PROPERTY_PING_SWITCH, "0");
                }
            }
        });

        if(mHandler!=null && counter!=0 && total>counter) {
            mHandler.postDelayed(runnable, 1000);
        }

        // Capture text edit key press
        mEditTotal = (EditText) findViewById(R.id.edit_total);
        mEditTotal.setText(""+total);
        mEditTotal.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN)
                        && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    Toast.makeText(Shutdown.this, mEditTotal.getText(), Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });


        keyguardManager = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        lockTag =  "Shutdown";
        keyguardLock = keyguardManager.newKeyguardLock(lockTag);
        keyguardLock.disableKeyguard();

        mIntentFilter = new IntentFilter();
        //mIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mIntentFilter.addAction(ACTION_ALARM);
        //mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        //mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, mIntentFilter);

        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_ALARM), PendingIntent.FLAG_UPDATE_CURRENT);
   }
 
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_AUTO_REFRESH:
                mButtonStart.setEnabled(false);
                --duration;
                mTextView01.setText(" "+duration);
                if(duration == 0) {
                    ++counter;
                    SystemProperties.set(PROPERTY_ACTION_COUNTER, Integer.toString(counter));
                    onActionThread();
                } else if(mPingSwitch && (duration == 20)) {
                    ping();
                }
                break;
            }
        }
    };
    
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
                if(duration > 0) {
                        mHandler.obtainMessage(MSG_AUTO_REFRESH, null).sendToTarget();
                        mHandler.postDelayed(this, 1000);
                }
        }
    };
    
    @Override  
    public void onDestroy() {  
         Log.d(TAG, "onDestroy");
         unregisterReceiver(mIntentReceiver);
         //cancel the alarm
         unregisterAlarm();
         super.onDestroy();
    }

    public void onCancel(View v) {
        counter = 0;
        SystemProperties.set(PROPERTY_ACTION_COUNTER, Integer.toString(counter));
        mCounter.setText("Counter: "+counter);

        ping_fail = 0;
        SystemProperties.set(PROPERTY_PING_FAIL, Integer.toString(ping_fail));
        mPingFail.setText("Fail to ping: "+ping_fail);

        String time = SystemProperties.get(PROPERTY_ACTION_DURATION, "60");
        duration = Integer.valueOf(time).intValue();
        mTextView01.setText(" "+duration);

        mHandler.removeCallbacks(runnable);
        mButtonStart.setEnabled(true);

        //cancel the alarm
        unregisterAlarm();
    }

    public void onStartTest(View v) {
        //mHandler.removeCallbacks(runnable);
        if(mEditTotal==null || mCounter==null || mTextView01==null) {
            return;
        }

        String strEdit = mEditTotal.getText().toString();
        Log.d(TAG, ""+strEdit);
        int temp = Integer.valueOf(strEdit).intValue();
        Log.d(TAG, "temp: "+temp+ " total: "+total);
        if(total != temp) {
            total = temp;
            SystemProperties.set(PROPERTY_ACTION_TOTAL, Integer.toString(total));
        }
        counter = 0;
        SystemProperties.set(PROPERTY_ACTION_COUNTER, Integer.toString(counter));
        mCounter.setText("Counter: "+counter);

        ping_fail = 0;
        SystemProperties.set(PROPERTY_PING_FAIL, Integer.toString(ping_fail));
        mPingFail.setText("Fail to ping: "+ping_fail);

        String time = SystemProperties.get(PROPERTY_ACTION_DURATION, "60");
        duration = Integer.valueOf(time).intValue();
        mTextView01.setText(" "+duration);

        if(mHandler != null && runnable!=null && total>counter && total>0) {
            mHandler.removeCallbacks(runnable);
            mHandler.postDelayed(runnable, 1000);
        }
    }

    private String avgSpeed(String str) {
        int position = str.indexOf("min/avg/max");
        if (position != -1) {
            String subStr = str.substring(position + 18);
            position = subStr.indexOf("/");
            subStr = subStr.substring(position + 1);
            position = subStr.indexOf("/");
            return subStr.substring(0, position);
        } else {
            return null;
        }
    }

    private boolean ping() {
        String[] PING = {"ping", "-w", "4", "www.sohu.com"};
        String result = null;
        ping_result = CommandManager.run_command(PING, "/system/bin");

        //try {
        //    Thread.sleep(2000);
        //} catch (InterruptedException e) {
        //    e.printStackTrace();
        //}
        Log.d(TAG, "END COMMAND");
        if (avgSpeed(ping_result) != null) {
            Log.d(TAG, "Ping SUCCESS!");
            return true;
        } else {
            Log.d(TAG, "Ping FAIL!");
            ping_fail ++;
            SystemProperties.set(PROPERTY_PING_FAIL, Integer.toString(ping_fail));
            mPingFail.setText("Fail to ping: "+ping_fail);
            return false;
        }

    }

    public void onShutDown(View v) {
        if(option == 0) {
            Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
            intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            //reboot the device after a while
            Intent intent=new Intent(Intent.ACTION_REBOOT);
            intent.putExtra("nowait", 1);
            intent.putExtra("interval", 1);
            intent.putExtra("window", 0);
            sendBroadcast(intent);
        }
        
//        try {
//            Process proc = Runtime.getRuntime()
//                    .exec(new String[] { "su", "-c", "reboot -p" });
//            proc.waitFor();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
        
//         Intent intent2 = new Intent(Intent.ACTION_REBOOT);
//         intent2.putExtra("nowait", 1); intent2.putExtra("interval", 1);
//         intent2.putExtra("window", 0); sendBroadcast(intent2);

        
//          try{ //Load classes and objects
//                  Object power; 
//                  Context fContext = getApplicationContext(); 
//                  Class <?>   ServiceManager = Class.forName("android.os.ServiceManager"); 
//                  Class<?> Stub = Class.forName("android.os.IPowerManager$Stub");
//
//                  Method getService = ServiceManager.getMethod("getService", new Class[] {String.class}); 
//                  //Method asInterface =   GetStub.getMethod("asInterface", new Class[] {IBinder.class});//of   this class? 
//                  Method asInterface = Stub.getMethod("asInterface", new Class[] {IBinder.class}); //of this class? 
//                  IBinder iBinder =          (IBinder) getService.invoke(null, new Object[] {Context.POWER_SERVICE});
//                  //power =  asInterface.invoke(null,iBinder);//or call constructor Stub?//
//                  
//                  Method shutdown = power.getClass().getMethod("shutdown", new Class[]{boolean.class, boolean.class});
//                  
//                  int Brightness = 5; 
//                  shutdown.invoke(false, false); // Method setBacklightBrightness.in 
//                  //log("Load internal IPower classes Ok");
//          }catch(InvocationTargetException e) { 
//                  // IPowerManager powerManager =  IPowerManager.Stub.asInterface( //
//                  ServiceManager.getService(Context.POWER_SERVICE)); 
//          } catch (ClassNotFoundException e) {
//                  e.printStackTrace(); 
//          } catch(NoSuchMethodException e) { 
//                  e.printStackTrace(); 
//          } catch  (IllegalAccessException e) { 
//                  e.printStackTrace();
//          }
         

//         PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
//         pm.reboot("shutdown");

//          IPowerManager powerManager = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
//          try {
//                  powerManager.shutdown(false, false); 
//          } catch (RemoteException e) { }
 
//        try {
//            Class<?> ServiceManager = Class
//                    .forName("android.os.ServiceManager");
//            Method getService = ServiceManager.getMethod("getService",
//                    java.lang.String.class);
//            Object oRemoteService = getService.invoke(null,
//                    Context.POWER_SERVICE);
//            Class<?> cStub = Class.forName("android.os.IPowerManager$Stub");
//            Method asInterface = cStub.getMethod("asInterface",
//                    android.os.IBinder.class);
//            Object oIPowerManager = asInterface.invoke(null, oRemoteService);
//            Method shutdown = oIPowerManager.getClass().getMethod("shutdown",
//                    boolean.class, boolean.class);
//            shutdown.invoke(oIPowerManager, false, true);
//        } catch (Exception e) {
//            Log.e(LOG_TAG, e.toString(), e);
//        }

    }

}
