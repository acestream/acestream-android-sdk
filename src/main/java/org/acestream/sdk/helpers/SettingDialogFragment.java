package org.acestream.sdk.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SettingDialogFragment extends DialogFragment {

    private final static String TAG = "AceStream/Settings";

    private final static String browseUpElement = " . . ";
    private TextView mCurrentValueView;
    private ListView mListView;
    private String mCurrentValue;

    public interface SettingDialogListener {
        void onSaveSetting(String type, String name, Object value, boolean sendToEngine);
    }

    private SettingDialogListener mListener = null;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if(mListener == null) {
            // Assume that calling activity is a listener
            try {
                mListener = (SettingDialogListener) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString() + " must implement SettingDialogListener");
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void setListener(SettingDialogListener listener) {
        mListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Resources res = getResources();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(AceStream.context());

        final String type = getArguments().getString("type");
        final String name = getArguments().getString("name");
        final String defaultValue = getArguments().getString("defaultValue");
        final boolean sendToEngine = getArguments().getBoolean("sendToEngine");
        String title = getArguments().getString("title");
        String value = sp.getString(name, null);
        if(title == null) {
            title = "?";
        }
        final EditText txtValue;

        // get default system language if not found in settings
        if(name.equals("language") && TextUtils.isEmpty(value)) {
            value = Locale.getDefault().getLanguage();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if(type.equals("list")) {
            txtValue = null;
            int entriesResourcesId = getArguments().getInt("entries");
            int entryValuesResourcesId = getArguments().getInt("entryValues");
            final String[] entries = getArguments().getStringArray("entriesList");

            final String[] entryValues;
            if(entriesResourcesId == 0) {
                entryValues = getArguments().getStringArray("entryValuesList");
            }
            else {
                entryValues = res.getStringArray(entryValuesResourcesId);
            }

            int selectedId = -1;
            if(value != null) {
                selectedId = java.util.Arrays.asList(entryValues).indexOf(value);
            }
            else if(defaultValue != null) {
                selectedId = java.util.Arrays.asList(entryValues).indexOf(defaultValue);
            }

            builder.setTitle(title);

            DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (mListener != null) {
                        try {
                            if(name.equals("cache_dir") && i == entryValues.length - 1) {
                                // custom
                                SettingDialogFragment dialogFragment = new SettingDialogFragment();
                                Bundle bundle = new Bundle();
                                bundle.putString("name", "cache_dir");
                                bundle.putString("type", "folder");
                                bundle.putString("title", getResources().getString(R.string.prefs_item_cache_dir));
                                bundle.putBoolean("sendToEngine", sendToEngine);

                                dialogFragment.setArguments(bundle);
                                dialogFragment.show(getActivity().getFragmentManager(), "setting_dialog");

                                dialogInterface.dismiss();
                            }
                            else {
                                mListener.onSaveSetting(type, name, entryValues[i], sendToEngine);
                                dialogInterface.dismiss();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "error", e);
                        }
                    }
                }
            };

            if(entries != null) {
                builder.setSingleChoiceItems(entries, selectedId, clickListener);
            }
            else {
                builder.setSingleChoiceItems(entriesResourcesId, selectedId, clickListener);
            }
        }
        else if(type.equals("int")) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.setting_dialog_number, null);
            TextView txtMessage = (TextView)view.findViewById(R.id.message);
            txtValue = (EditText)view.findViewById(R.id.txt_value);

            txtMessage.setText(title);
            if(value != null) {
                txtValue.setText(value);
            }
            builder.setView(view)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (mListener != null) {
                            mListener.onSaveSetting(type, name, txtValue.getText().toString(), sendToEngine);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });
        }
        else if(type.equals("folder")) {
            txtValue = null;
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.l_folder_dialog, null);

            mCurrentValue = value;
            if(mCurrentValue == null || !new File(mCurrentValue).exists()) {
                mCurrentValue = AceStream.externalFilesDir();
                if(mCurrentValue == null) {
                    mCurrentValue = "/";
                }
            }

            mListView = (ListView)view.findViewById(R.id.dialog_list);
            mListView.setOnItemClickListener( new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                    String text = (String)a.getItemAtPosition(position);
                    if(text.equalsIgnoreCase(browseUpElement)) {
                        browseUp();
                        return;
                    }
                    browseDown(mCurrentValue + File.separator + text);
                }
            });

            mCurrentValueView = (TextView)view.findViewById(R.id.txt_current_path);
            browseDown(mCurrentValue);

            builder.setTitle(title)
                    .setView(view)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (mListener != null) {

                                try {
                                    if (name != null && name.equals("cache_dir")) {
                                        if (mCurrentValue != null) {
                                            File f = new File(mCurrentValue);
                                            boolean isWritable = false;

                                            if(f.canWrite()) {
                                                // sometimes |canWrite| returns true but we cannot actually write
                                                // try to create dir to ensure
                                                File tmp = new File(f, ".acestream_cache");
                                                if(tmp.exists() || tmp.mkdirs()) {
                                                    isWritable = true;
                                                }
                                                else {
                                                    isWritable = false;
                                                    Log.d(TAG, "dir selected: cannot create subdir");
                                                }
                                            }

                                            Log.d(TAG, "dir selected: writable=" + isWritable + " path=" + f.getAbsolutePath());

                                            if (!isWritable) {
                                                Toast.makeText(
                                                        AceStream.context(),
                                                        getResources().getString(R.string.directory_is_not_writable),
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                                return;
                                            }
                                        }
                                    }
                                }
                                catch(Throwable e) {
                                    Log.e(TAG, "error", e);
                                }
                                mListener.onSaveSetting(type, name, mCurrentValue, sendToEngine);
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
        }
        else {
            Log.e(TAG, "Unknown type: " + type);
            return null;
        }

        // Create the AlertDialog object and return it
        Dialog dialog = builder.create();

        if(txtValue != null) {
            // show keyboard
            txtValue.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        return dialog;
    }

    private boolean browseDown(String dir)
    {
        File path = new File(dir);
        if(!path.isDirectory())
            return false;

        updateView(dir);
        mCurrentValue = dir;
        mCurrentValueView.setText(mCurrentValue);
        return true;
    }

    private void browseUp()
    {
        File path = new File(mCurrentValue);
        if(path.getParentFile() != null)
            browseDown(path.getParentFile().getAbsolutePath());
    }

    private void updateView(String dir) {
        File path = new File(dir);
        List<String> list = new ArrayList<>();
        if(path.getParentFile() != null)
            list.add(browseUpElement);

        String[] files = path.list();
        if(files != null) {
            for (String file : files) {
                if (!new File(path.getAbsolutePath() + File.separator + file).canRead()
                        || !new File(path.getAbsolutePath() + File.separator + file).isDirectory())
                    continue;
                list.add(file);
            }
        }

        Collections.sort(list, new ListComparator());
        mListView.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.l_folder_dialog_item, list));
    }

    private static class ListComparator implements Comparator<String> {
        @Override
        public int compare(String lhs, String rhs) {
            if(lhs.equalsIgnoreCase(browseUpElement))
                return -1;
            else if(rhs.equalsIgnoreCase(browseUpElement))
                return 1;

            File lfile = new File(lhs);
            File rfile = new File(rhs);

            if(lfile.isDirectory() && !rfile.isDirectory())
                return 1;
            else if(!lfile.isDirectory() && rfile.isDirectory())
                return -1;
            else
                return lhs.compareTo(rhs);
        }
    }

}
