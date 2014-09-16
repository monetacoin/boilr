package mobi.boilr.boilr.services;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mobi.boilr.boilr.database.DBManager;
import mobi.boilr.boilr.domain.AndroidNotify;
import mobi.boilr.boilr.utils.Log;
import mobi.boilr.boilr.views.fragments.SettingsFragment;
import mobi.boilr.libdynticker.bitstamp.BitstampExchange;
import mobi.boilr.libdynticker.btcchina.BTCChinaExchange;
import mobi.boilr.libdynticker.btce.BTCEExchange;
import mobi.boilr.libdynticker.core.Exchange;
import mobi.boilr.libdynticker.core.Pair;
import mobi.boilr.libpricealarm.Alarm;
import mobi.boilr.libpricealarm.PriceHitAlarm;
import mobi.boilr.libpricealarm.PriceVarAlarm;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class StorageAndControlService extends Service {

	private Map<Integer, Alarm> alarmsMap;
	private Map<String, Exchange> exchangesMap;
	private int prevAlarmID = 0;
	private AlarmManager alarmManager;
	private DBManager db;
	// Private action used to update last value from the Exchange.
	private static final String UPDATE_LAST_VALUE = "UPDATE_LAST_VALUE";

	private class UpdateLastValueTask extends AsyncTask<Alarm, Void, Void> {
		@Override
		protected Void doInBackground(Alarm... alarms) {
			if(alarms.length == 1)
				alarms[0].run();
			return null;
		}
	}

	private class PopupalteDBTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... arg) {
			Log.d("Populating DB.");
			try {
				Alarm alarm = new PriceHitAlarm(generateAlarmID(), new BitstampExchange(10000000), new Pair("BTC", "USD"), 60000, new AndroidNotify(StorageAndControlService.this), 476, 475);
				addAlarm(alarm);
				startAlarm(alarm);

				alarm = new PriceHitAlarm(generateAlarmID(), new BTCEExchange(10000000), new Pair("BTC", "EUR"), 60000, new AndroidNotify(StorageAndControlService.this), 374, 373);
				addAlarm(alarm);
				startAlarm(alarm);

				alarm = new PriceVarAlarm(generateAlarmID(), new BTCChinaExchange(10000000), new Pair("BTC", "CNY"), 60000, new AndroidNotify(StorageAndControlService.this), 0.03f);
				addAlarm(alarm);
				startAlarm(alarm);

			} catch(Exception e) {
				Log.e("Caught exception while populating DB.", e);
			}
			return null;
		}


	}

	@SuppressLint("UseSparseArrays")
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d("Creating StorageAndControlService.");
		alarmsMap = new HashMap<Integer, Alarm>();
		exchangesMap = new HashMap<String, Exchange>();
		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		try {
			db = new DBManager(this);
			prevAlarmID = db.getNextID();
			alarmsMap = db.getAlarms();
			if(prevAlarmID == 0) {
				new PopupalteDBTask().execute();
				return;
			}
			// Set Exchange and start alarm
			for(Alarm alarm : alarmsMap.values()) {
				alarm.setExchange(getExchange(alarm.getExchangeCode()));
				if(alarm.isOn()) {
					this.startAlarm(alarm);
				}
			}
		} catch (Exception e) {
			Log.e("Caught exception while recovering alarms from DB.", e);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(UPDATE_LAST_VALUE.equals(intent.getAction())) {
			int alarmID = intent.getIntExtra("alarmID", Integer.MIN_VALUE);
			if(alarmID != Integer.MIN_VALUE) {
				Alarm alarm = getAlarm(alarmID);
				new UpdateLastValueTask().execute(alarm);
				Log.d("Last value for alarm " + alarmID + " " + alarm.getLastValue());
			}
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new LocalBinder<StorageAndControlService>(this);
	}

	public Exchange getExchange(String classname) throws ClassNotFoundException,
	InstantiationException, IllegalAccessException, IllegalArgumentException,
	InvocationTargetException, SecurityException {
		if(exchangesMap.containsKey(classname)) {
			return exchangesMap.get(classname);
		} else {
			@SuppressWarnings("unchecked")
			Class<? extends Exchange> c = (Class<? extends Exchange>) Class.forName(classname);
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			long pairInterval = Long.parseLong(sharedPreferences.getString(SettingsFragment.PREF_KEY_CHECK_PAIRS_INTERVAL, ""));
			Exchange exchange = (Exchange) c.getDeclaredConstructors()[0].newInstance(pairInterval);
			exchangesMap.put(classname, exchange);
			return exchange;
		}
	}

	public List<Alarm> getAlarms() {
		return new ArrayList<Alarm>(alarmsMap.values());
	}

	public Alarm getAlarm(int alarmID) {
		return alarmsMap.get(alarmID);
	}

	public int generateAlarmID() {
		return ++prevAlarmID;
	}

	public void startAlarm(Alarm alarm) {
		Intent intent = new Intent(this, StorageAndControlService.class);
		intent.setAction(UPDATE_LAST_VALUE);
		intent.putExtra("alarmID", alarm.getId());
		PendingIntent pendingIntent = PendingIntent.getService(this, alarm.getId(), intent, 0);
		alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.getPeriod(), alarm.getPeriod(), pendingIntent);
		alarm.turnOn();
	}

	public void startAlarm(int alarmID) {
		startAlarm(alarmsMap.get(alarmID));
	}

	public void stopAlarm(Alarm alarm) {
		stopAlarm(alarm.getId());
	}

	public void stopAlarm(int alarmID) {
		Intent intent = new Intent(this, StorageAndControlService.class);
		intent.setAction(UPDATE_LAST_VALUE);
		PendingIntent pendingIntent = PendingIntent.getService(this, alarmID, intent, 0);
		alarmManager.cancel(pendingIntent);
		alarmsMap.get(alarmID).turnOff();
	}

	public void addAlarm(Alarm alarm) throws IOException {
		alarmsMap.put(alarm.getId(), alarm);
		db.storeAlarm(alarm);
	}

	public void replaceAlarm(Alarm alarm) throws IOException {
		db.updateAlarm(alarm);
	}

	public void deleteAlarm(Alarm alarm) throws IOException {
		db.deleteAlarm(alarm);
		alarmsMap.remove(alarm.getId());
	}

	public void deleteAlarm(int id) throws IOException {
		deleteAlarm(alarmsMap.get(id));
	}

}