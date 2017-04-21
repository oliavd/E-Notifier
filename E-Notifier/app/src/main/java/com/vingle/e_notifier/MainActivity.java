package com.vingle.e_notifier;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mbientlab.metawear.api.MetaWearBleService;
import com.mbientlab.metawear.api.MetaWearController;
import com.mbientlab.metawear.api.Module;
import com.mbientlab.metawear.api.controller.Accelerometer;
import com.mbientlab.metawear.api.controller.Accelerometer.Axis;
import com.mbientlab.metawear.api.controller.Accelerometer.MovementData;
import com.mbientlab.metawear.api.controller.MechanicalSwitch;
import com.vingle.e_notifier.MetaScanner.ScannerCallback;
import com.vingle.e_notifier.MetaSetting.SettingsState;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
import static android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
import static android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER;

/* This is the main code file which controls the app */

public class MainActivity extends Activity implements ScannerCallback, ServiceConnection, SettingsState {
    private final static String FRAGMENT_KEY = "com.vingle.e_notifier.MainActivity.FRAGMENT_KEY";
    private final static int REQUEST_ENABLE_BT = 0;

    private PlaceholderFragment mainFragment = null;
    private MetaWearBleService mwService = null;
    private LocalBroadcastManager broadcastManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            mainFragment = new PlaceholderFragment();
            getFragmentManager().beginTransaction().add(R.id.container, mainFragment).commit();
        } else {
            mainFragment = (PlaceholderFragment) getFragmentManager().getFragment(savedInstanceState, FRAGMENT_KEY);
        }

        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if (btAdapter == null) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
                    .setMessage(R.string.error_no_bluetooth)
                    .setCancelable(false)
                    .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.finish();
                        }
                    })
                    .create()
                    .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (broadcastManager != null) {
            broadcastManager.unregisterReceiver(MetaWearBleService.getMetaWearBroadcastReceiver());
        }
        getApplicationContext().unbindService(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_CANCELED) {
                    finish();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_connect:
                new MetaScanner().show(getFragmentManager(), "metawear_scanner_fragment");
                break;
            case R.id.action_help:
                new AlertDialog.Builder(this).setTitle(R.string.label_help)
                        .setMessage(R.string.text_help)
                        .setCancelable(true)
                        .create()
                        .show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void btDeviceSelected(BluetoothDevice device) {
        mainFragment.setBtDevice(device);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mainFragment != null) {
            getFragmentManager().putFragment(outState, FRAGMENT_KEY, mainFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mwService = ((MetaWearBleService.LocalBinder) service).getService();

        broadcastManager = LocalBroadcastManager.getInstance(mwService);
        broadcastManager.registerReceiver(MetaWearBleService.getMetaWearBroadcastReceiver(),
                MetaWearBleService.getMetaWearIntentFilter());
        mwService.useLocalBroadcastManager(broadcastManager);
    }


    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    @Override
    public void setButtonMessage(int position) {
        mainFragment.textMsgPosition = position;
    }

    @Override
    public int getButtonMessage() {
        return mainFragment.textMsgPosition;
    }

    @Override
    public void setShakeMessage(int position) {
        mainFragment.shakeMsgPosition = position;
    }

    @Override
    public int getShakeMessage() {
        return mainFragment.shakeMsgPosition;
    }

    private static class ContactInfo {
        public String name;
        public String number;

        @Override
        public String toString() {
            return name;
        }
    }

    private static class ContactListAdapter extends ArrayAdapter<ContactInfo> {
        private final LayoutInflater mInflator;

        public ContactListAdapter(Context context, int resource, LayoutInflater inflator) {
            super(context, resource);
            this.mInflator = inflator;
        }

        public ContactListAdapter(Context context, int resource, LayoutInflater inflator, Cursor peopleCursor) {
            this(context, resource, inflator);

            if (peopleCursor.getCount() > 0) {
                while (peopleCursor.moveToNext()) {
                    ContactInfo newInfo = new ContactInfo();
                    newInfo.name = peopleCursor.getString(peopleCursor.getColumnIndex(DISPLAY_NAME));
                    newInfo.number = peopleCursor.getString(peopleCursor.getColumnIndex(NUMBER));

                    add(newInfo);
                }
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mInflator.inflate(R.layout.contact_info, null);
                viewHolder = new ViewHolder();
                viewHolder.contactName = (TextView) convertView.findViewById(R.id.contact_name);
                viewHolder.contactNumber = (TextView) convertView.findViewById(R.id.contact_number);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            ContactInfo info = getItem(position);

            viewHolder.contactNumber.setText(info.number);
            viewHolder.contactName.setText(info.name);
            return convertView;
        }

        private class ViewHolder {
            public TextView contactName;
            public TextView contactNumber;
        }

    }


     // This is to add shake to text functionality

    public static class PlaceholderFragment extends Fragment implements ServiceConnection {
        private static final byte MAX_SHAKES = 10;
        private static final long RESET_DELAY = 3000;

        private MetaWearBleService mwService = null;
        private MetaWearController mwController = null;
        private ContactListAdapter contacts = null, saviours = null;
        private ListView savioursListView = null;
        private AutoCompleteTextView contactName = null;
        private AtomicInteger shakeCounts;
        private AtomicBoolean setTimerTask;
        private final Timer timer = new Timer();
        private Accelerometer accelCtrllr;
        private MechanicalSwitch switchCtrllr;

        private TimerTask resetTask;

        public int textMsgPosition = 0, shakeMsgPosition = 0;

        public PlaceholderFragment() {
        }

        public void setBtDevice(BluetoothDevice device) {
            shakeCounts = new AtomicInteger(0);
            setTimerTask = new AtomicBoolean(false);

            mwController = mwService.getMetaWearController(device);
            mwController.setRetainState(false);
            mwController.addDeviceCallback(new MetaWearController.DeviceCallbacks() {
                @Override
                public void connected() {
                    switchCtrllr = ((MechanicalSwitch) mwController.getModuleController(Module.MECHANICAL_SWITCH));
                    switchCtrllr.enableNotification();

                    accelCtrllr = ((Accelerometer) mwController.getModuleController(Module.ACCELEROMETER));
                    accelCtrllr.enableShakeDetection(Axis.X);
                    accelCtrllr.startComponents();

                    Toast.makeText(getActivity(), R.string.toast_connected, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void disconnected() {
                    Toast.makeText(getActivity(), R.string.toast_disconnected, Toast.LENGTH_SHORT).show();
                }
            }).addModuleCallback(new MechanicalSwitch.Callbacks() {
                @Override
                public void pressed() {
                    if (saviours != null && saviours.getCount() > 0) {
                        sendText(true);
                    } else {
                        Toast.makeText(getActivity(), R.string.error_no_contact, Toast.LENGTH_SHORT).show();
                    }
                }
            }).addModuleCallback(new Accelerometer.Callbacks() {
                @Override
                public void shakeDetected(MovementData moveData) {
                    shakeCounts.getAndIncrement();
                    if (!setTimerTask.get()) {
                        resetTask = new TimerTask() {
                            @Override
                            public void run() {
                                shakeCounts.set(0);
                                setTimerTask.getAndSet(false);
                            }
                        };
                        timer.schedule(resetTask, RESET_DELAY);
                        setTimerTask.getAndSet(true);
                    }
                    if (shakeCounts.get() == MAX_SHAKES) {
                        resetTask.cancel();
                        setTimerTask.getAndSet(false);
                        shakeCounts.getAndSet(0);
                        if (saviours != null && saviours.getCount() > 0) {
                            sendText(false);
                        } else {
                            Toast.makeText(getActivity(), R.string.error_no_contact, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
            mwController.connect();
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            activity.getApplicationContext().bindService(new Intent(activity, MetaWearBleService.class),
                    this, Context.BIND_AUTO_CREATE);
        }


        @Override
        public void onDestroy() {
            super.onDestroy();

            getActivity().getApplicationContext().unbindService(this);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            setRetainInstance(true);
            setHasOptionsMenu(true);

            if (saviours == null) {
                saviours = new ContactListAdapter(getActivity(), R.id.contact_info_layout, inflater);
            }
            if (contacts == null) {
                Cursor peopleCursor = getActivity().getContentResolver().query(CONTENT_URI,
                        new String[]{DISPLAY_NAME, NUMBER}, null, null, null);
                contacts = new ContactListAdapter(getActivity(), R.id.contact_info_layout, inflater, peopleCursor);
            }

            return inflater.inflate(R.layout.fragment_main, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            savioursListView = (ListView) view.findViewById(R.id.phone_contacts);
            savioursListView.setAdapter(saviours);
            savioursListView.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent,
                                               View view, int position, long id) {
                    saviours.remove(saviours.getItem(position));
                    saviours.notifyDataSetChanged();
                    return true;
                }
            });

            contactName = (AutoCompleteTextView) view.findViewById(R.id.AutoCompleteTextView1);
            contactName.setThreshold(1);
            contactName.setAdapter(contacts);
            contactName.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    if (saviours.getPosition(contacts.getItem(position)) == -1) {
                        saviours.add(contacts.getItem(position));
                        saviours.notifyDataSetChanged();
                        contactName.getEditableText().clear();
                    }
                }
            });
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_disconnect:
                    switchCtrllr.disableNotification();
                    accelCtrllr.stopComponents();
                    mwController.waitToClose(true);
                    return true;
                case R.id.action_settings:
                    FragmentManager fm = getActivity().getFragmentManager();
                    new MetaSetting().show(fm, "settings_fragment");
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mwService = ((MetaWearBleService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mwService = null;
        }

        // Using sms manager to send text message

        private void sendText(boolean buttonPress) {

            SmsManager smsMng = SmsManager.getDefault();
            byte errors = 0, successful = 0;
            String txtMsg = getActivity().getResources().getStringArray(R.array.message_array)[buttonPress ? textMsgPosition : shakeMsgPosition];

            for (int i = 0; i < saviours.getCount(); i++) {
                try {
                    smsMng.sendTextMessage(saviours.getItem(i).number, null, txtMsg, null, null);
                    successful++;
                } catch (IllegalArgumentException ex) {
                    Log.e("E-Notifier", String.format("Couldn't send text to '%s', msg= %s", saviours.getItem(i).number, ex.getMessage()));
                    errors++;
                }
            }

            if (errors != 0) {
                Toast.makeText(getActivity(), R.string.error_sending_text, Toast.LENGTH_SHORT).show();
            } else {
                final String successfulText = getActivity().getResources().getString(R.string.text_successful);
                Toast.makeText(getActivity(), String.format(Locale.US, successfulText, successful), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
