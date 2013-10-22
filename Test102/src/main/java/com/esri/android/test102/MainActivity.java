package com.esri.android.test102;

import android.app.Activity;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.FeatureLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.gdb.GdbFeatureTable;
import com.esri.core.gdb.Geodatabase;
import com.esri.core.map.CallbackListener;
import com.esri.core.tasks.gdb.GenerateGeodatabaseParameters;
import com.esri.core.tasks.gdb.GeodatabaseStatusCallback;
import com.esri.core.tasks.gdb.GeodatabaseStatusInfo;
import com.esri.core.tasks.gdb.GeodatabaseTask;
import com.esri.core.tasks.gdb.SyncModel;


public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

     /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private MapView mMapView;

    private static GeodatabaseTask gdbTask;
    protected static final String TAG = "MainActivity";


    //some path stuff
    private static String DEFAULT_gdbFileName = "/data/Temp/TestAndroid.geodatabase";
    private static String gdbFileName = Environment.getExternalStorageDirectory().getPath() + DEFAULT_gdbFileName;
    //private static String basemapFileName = Environment.getExternalStorageDirectory().getPath() + DEFAULT_BASEMAP_FILENAME;


    private static String fsUrl = "http://services.arcgis.com/Wl7Y1m92PbjtJs5n/arcgis/rest/services/SampleMapOpsDashboardSDK/FeatureServer";

    private String gdbPath = "/Phone/data/Temp";





    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Retrieve the map and initial extent from XML layout
        mMapView = (MapView)findViewById(R.id.map);
        // Add dynamic layer to MapView
        mMapView.addLayer(new ArcGISTiledMapServiceLayer("" +
                "http://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer"));

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

    }

    private static void downloadGeodatabase(final MainActivity activity, final MapView mapView) {

        gdbTask = new GeodatabaseTask(fsUrl, null,
                new CallbackListener<FeatureServiceInfo>() {

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "", e);
                    }

                    @Override
                    public void onCallback(FeatureServiceInfo objs) {
                        // Is sync supported
                        if (objs.isSyncEnabled()) {
                            requestGdbInOneMethod(gdbTask, activity, mapView);
                        }
                    }
                });
    }

    private static void requestGdbInOneMethod(GeodatabaseTask gdbTask, final MainActivity activity, final MapView mapView) {
        int[] layerIds = {0};
        GenerateGeodatabaseParameters params = new GenerateGeodatabaseParameters(
                layerIds, mapView.getExtent(),
                mapView.getSpatialReference(),true,
                SyncModel.LAYER, mapView.getSpatialReference());

        showProgress(activity, true);

        // gdb complete callback
        CallbackListener<Geodatabase> gdbResponseCallback = new CallbackListener<Geodatabase>() {

            @Override
            public void onCallback(Geodatabase obj) {
                // update UI
                showMessage(activity, "Geodatabase downloaded!");
                Log.e(TAG, "geodatabase is: " + obj.getPath());

                showProgress(activity, false);

                // Remove all the feature layers from map and add a feature
                // Layer from the downloaded geodatabase

                for (Layer layer : mapView.getLayers()) {
                    if (layer instanceof ArcGISFeatureLayer)
                        mapView.removeLayer(layer);
                }
                for (GdbFeatureTable gdbFeatureTable : obj.getGdbTables()) {
                    if (gdbFeatureTable.hasGeometry())
                        mapView.addLayer(new FeatureLayer(gdbFeatureTable));
                }

            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "", e);
            }

        };

        GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {

            @Override
            public void statusUpdated(GeodatabaseStatusInfo status) {
                showMessage(activity, status.getStatus().toString());
            }
        };

        // Single method does it all!
        gdbTask.submitGenerateGeodatabaseJobAndDownload(params, gdbFileName,
                statusCallback, gdbResponseCallback);
        showMessage(activity, "Submitting gdb job...");
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = "something1";
                break;
            case 2:
                mTitle = "something2";
                break;
            case 3:
                mTitle = "something3";
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_replicate:
                downloadGeodatabase(this, mMapView);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void replicate(View view){
        TextView responseText = (TextView)findViewById(R.id.testtext);

    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }

    }

    static void showMessage(final MainActivity activity, final String message) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static void showProgress(final MainActivity activity,
                             final boolean b) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                activity.setProgressBarIndeterminateVisibility(b);
            }
        });



    }
}
