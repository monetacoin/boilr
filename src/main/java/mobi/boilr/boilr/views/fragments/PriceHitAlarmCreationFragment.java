package mobi.boilr.boilr.views.fragments;

import java.io.IOException;

import mobi.boilr.boilr.domain.AndroidNotifier;
import mobi.boilr.boilr.utils.Conversions;
import mobi.boilr.libdynticker.core.Exchange;
import mobi.boilr.libdynticker.core.Pair;
import mobi.boilr.libpricealarm.Alarm;
import mobi.boilr.libpricealarm.PriceHitAlarm;
import mobi.boilr.libpricealarm.UpperLimitSmallerOrEqualLowerLimitException;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

public class PriceHitAlarmCreationFragment extends AlarmCreationFragment {

	private class OnPriceHitSettingsPreferenceChangeListener extends
	OnAlarmSettingsPreferenceChangeListener {

		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			String key = preference.getKey();
			if(key.equals(PREF_KEY_UPPER_VALUE) || key.equals(PREF_KEY_LOWER_VALUE)) {
				preference.setSummary(newValue + " " + mPairs.get(mPairIndex).getExchange());
			} else {
				return super.onPreferenceChange(preference, newValue);
			}
			return true;
		}
	}

	@Override
	protected void updateDependentOnPair() {
		EditTextPreference[] edits = { mUpperLimitPref, mLowerLimitPref };
		if(!mRecoverSavedInstance && mLastValue != Double.POSITIVE_INFINITY) {
			for(EditTextPreference edit : edits) {
				edit.setText(Conversions.formatMaxDecimalPlaces(mLastValue));
			}
		}
		String text;
		for(EditTextPreference edit : edits) {
			edit.setEnabled(true);
			text = edit.getText();
			if(text != null && !text.equals(""))
				edit.setSummary(text + " " + mPairs.get(mPairIndex).getExchange());
		}
	}

	@Override
	protected void disableDependentOnPair() {
		disableDependentOnPairHitAlarm();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mListener = new OnPriceHitSettingsPreferenceChangeListener();
		super.onCreate(savedInstanceState);

		removePrefs(hitAlarmPrefsToKeep);
		if(savedInstanceState == null) {
			mUpperLimitPref.setText(null);
			mLowerLimitPref.setText(null);
			setUpdateIntervalPref();
		} else {
			// Upper and lower limit prefs summary will be updated by updateDependentOnPair()
			checkAndSetUpdateIntervalPref();
		}
		mAlarmTypePref.setValueIndex(0);
		mSpecificCat.setTitle(mAlarmTypePref.getEntry());
		mAlarmTypePref.setSummary(mAlarmTypePref.getEntry());
	}

	@Override
	public Alarm makeAlarm(int id, Exchange exchange, Pair pair, AndroidNotifier notifier)
			throws UpperLimitSmallerOrEqualLowerLimitException, IOException {
		String updateInterval = mUpdateIntervalPref.getText();
		// Time is in seconds, convert to milliseconds
		long period = 1000 * Long.parseLong(updateInterval != null ? updateInterval :
			mSharedPrefs.getString(SettingsFragment.PREF_KEY_DEFAULT_UPDATE_INTERVAL, ""));
		String upperLimitString = mUpperLimitPref.getText();
		double upperLimit;
		if(upperLimitString == null || upperLimitString.equals(""))
			upperLimit = Double.POSITIVE_INFINITY;
		else
			upperLimit = Double.parseDouble(upperLimitString);
		String lowerLimitString = mLowerLimitPref.getText();
		double lowerLimit;
		if(lowerLimitString == null || lowerLimitString.equals(""))
			lowerLimit = Double.NEGATIVE_INFINITY;
		else
			lowerLimit = Double.parseDouble(lowerLimitString);
		return new PriceHitAlarm(id, exchange, pair, period, notifier, upperLimit, lowerLimit);
	}
}
