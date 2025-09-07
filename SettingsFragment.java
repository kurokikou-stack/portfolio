package com.windnauts.gvidas;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.widget.TextView;

public class SettingsFragment extends PreferenceFragmentCompat {


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        EditTextPreference ELE_UP_Preference = findPreference("up_ele");
        EditTextPreference ELE_DOWN_Preference = findPreference("down_ele");
        EditTextPreference RUD_RIGHT_Preference = findPreference("right_rud");
        EditTextPreference RUD_LEFT_Preference = findPreference("left_rud");


        // 初期表示のために現在の値をsummaryに設定
        if (ELE_UP_Preference != null) {
            ELE_UP_Preference.setSummary(ELE_UP_Preference.getText());

            // テキストが変更された時にsummaryを更新
            ELE_UP_Preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });
        }
        // 初期表示のために現在の値をsummaryに設定
        if (ELE_DOWN_Preference != null) {
            ELE_DOWN_Preference.setSummary(ELE_DOWN_Preference.getText());

            // テキストが変更された時にsummaryを更新
            ELE_DOWN_Preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });
        }
        // 初期表示のために現在の値をsummaryに設定
        if (RUD_RIGHT_Preference != null) {
            RUD_RIGHT_Preference.setSummary(RUD_RIGHT_Preference.getText());

            // テキストが変更された時にsummaryを更新
            RUD_RIGHT_Preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });
        }
        // 初期表示のために現在の値をsummaryに設定
        if (RUD_LEFT_Preference != null) {
            RUD_LEFT_Preference.setSummary(RUD_LEFT_Preference.getText());

            // テキストが変更された時にsummaryを更新
            RUD_LEFT_Preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });
        }
    }
}
