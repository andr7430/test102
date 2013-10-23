package com.esri.android.test102;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.esri.android.map.FeatureLayer;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.MapOnTouchListener;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.gdb.GdbFeature;
import com.esri.core.gdb.GdbFeatureTable;
import com.esri.core.gdb.Geodatabase;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.MultiPath;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.Polyline;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.FeatureTemplate;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleFillSymbol;
import com.esri.core.symbol.SimpleLineSymbol;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.symbol.Symbol;
import com.esri.core.table.TableException;
import com.esri.core.tasks.gdb.GenerateGeodatabaseParameters;
import com.esri.core.tasks.gdb.GeodatabaseStatusCallback;
import com.esri.core.tasks.gdb.GeodatabaseStatusInfo;
import com.esri.core.tasks.gdb.GeodatabaseTask;
import com.esri.core.tasks.gdb.SyncGeodatabaseParameters;
import com.esri.core.tasks.gdb.SyncModel;

import java.util.ArrayList;


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

    MyTouchListener myListener;

    GraphicsLayer graphicsLayer;
    GraphicsLayer graphicsLayerEditing;
    GraphicsLayer highlightGraphics;

    private TemplatePicker tp;

    private static final int POINT = 0;
    private static final int POLYLINE = 1;
    private static final int POLYGON = 2;

    Button editButton;
    Button removeButton;
    Button clearButton;
    Button cancelButton;
    Button undoButton;
    Button saveButton;
    //Button openButton;
    //ToggleButton switchBasemapbutton;
    TabHost mTabHost;

    int editingmode;

    boolean featureUpdate = false;

    FeatureTemplate template;

    long featureUpdateId;
    int addedGraphicId;

    private PopupForEditOffline popup;

    ArrayList<Point> points = new ArrayList<Point>();
    ArrayList<Point> mpoints = new ArrayList<Point>();
    boolean midpointselected = false;
    boolean vertexselected = false;
    int insertingindex;
    ArrayList<EditingStates> editingstates = new ArrayList<EditingStates>();



    private static GeodatabaseTask gdbTask;
    protected static final String TAG = "MainActivity";


    //some path stuff
    private static String DEFAULT_gdbFileName = "/data/Temp/TestAndroid.geodatabase";
    private static String gdbFileName = Environment.getExternalStorageDirectory().getPath() + DEFAULT_gdbFileName;
    //private static String basemapFileName = Environment.getExternalStorageDirectory().getPath() + DEFAULT_BASEMAP_FILENAME;


    private static String fsUrl = "http://services.arcgis.com/Wl7Y1m92PbjtJs5n/ArcGIS/rest/services/SandyMapProject/FeatureServer";

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
        mMapView.addLayer(new ArcGISTiledMapServiceLayer("" +"http://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer"));

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));


        removeButton = (Button) findViewById(R.id.removebutton);

        editButton = (Button) findViewById(R.id.editbutton);

        cancelButton = (Button) findViewById(R.id.cancelbutton);

        clearButton = (Button) findViewById(R.id.clearbutton);

        saveButton = (Button) findViewById(R.id.savebutton);

        undoButton = (Button) findViewById(R.id.undobutton);
        mTabHost = (TabHost) findViewById(R.id.tabHost);



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
            case R.id.action_sync:
                syncGeodatabase(this);
                return true;
            case R.id.editbutton:
                MainActivity.showProgress(MainActivity.this, true);
                //clear();
                int layerCount = 0;
                for (Layer layer : mMapView.getLayers()) {
                    if (layer instanceof FeatureLayer) {
                        layerCount++;
                    }

                }
                if (layerCount > 0) {
                    if (myListener == null) {
                        myListener = new MyTouchListener(MainActivity.this,
                                mMapView);
                        mMapView.setOnTouchListener(myListener);
                    }
                    if (getTemplatePicker() != null) {
                        getTemplatePicker().showAtLocation(editButton, Gravity.BOTTOM,
                                0, 0);
                    } else {
                        new TemplatePickerTask().execute();
                    }
                } else {
                    MainActivity.showMessage(MainActivity.this,
                            "No Editable Local Feature Layers.");

                }
                MainActivity.showProgress(MainActivity.this, false);
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    public void replicate(View view){
        TextView responseText = (TextView)findViewById(R.id.testtext);
        responseText.setText(mMapView.getLayers().toString());

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

    public void editButton(View view) {

        MainActivity.showProgress(MainActivity.this, true);
        clear();
        int layerCount = 0;
        for (Layer layer : mMapView.getLayers()) {
            if (layer instanceof FeatureLayer) {
                layerCount++;
            }

        }
        if (layerCount > 0) {
            if (myListener == null) {
                myListener = new MyTouchListener(MainActivity.this,
                        mMapView);
                mMapView.setOnTouchListener(myListener);
            }
            if (getTemplatePicker() != null) {
                getTemplatePicker().showAtLocation(editButton, Gravity.BOTTOM,
                        0, 0);
            } else {
                new TemplatePickerTask().execute();
            }
        } else {
            MainActivity.showMessage(MainActivity.this,
                    "No Editable Local Feature Layers.");

        }
        MainActivity.showProgress(MainActivity.this, false);

    }

    void clear() {
        if (graphicsLayer != null) {
            graphicsLayer.removeAll();
        }

        if (graphicsLayerEditing != null) {
            graphicsLayerEditing.removeAll();
        }
        if (highlightGraphics != null) {
            highlightGraphics.removeAll();
            mMapView.getCallout().hide();

        }

        featureUpdate = false;
        points.clear();
        mpoints.clear();
        midpointselected = false;
        vertexselected = false;
        insertingindex = 0;
        clearButton.setEnabled(false);
        removeButton.setEnabled(false);
        cancelButton.setEnabled(false);
        undoButton.setEnabled(false);
        saveButton.setEnabled(false);
        editingstates.clear();

    }

    class MyTouchListener extends MapOnTouchListener {
        MapView map;
        Context context;
        Bitmap snapshot = null;
        boolean redrawCache = true;
        boolean showmag = false;

        public MyTouchListener(Context context, MapView view) {
            super(context, view);
            this.context = context;
            map = view;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (tp != null) {

                // if (vertexselected || midpointselected) {
                if (getTemplatePicker().getselectedTemplate() != null) {
                    setEditingMode();
                }

                if (getTemplatePicker().getSelectedLayer() != null) {
                    if (editingmode == POINT || editingmode == POLYLINE
                            || editingmode == POLYGON) {

                        highlightGraphics.removeAll();
                        long[] featureIds = ((FeatureLayer) mMapView
                                .getLayerByID(getTemplatePicker()
                                        .getSelectedLayer().getID()))
                                .getFeatureIDs(e.getX(), e.getY(), 25);

                        if (featureIds.length > 0) {
                            final long gdbFeatureSelectedId = featureIds[0];
                            GdbFeature gdbFeatureSelected = (GdbFeature) ((FeatureLayer) mMapView
                                    .getLayerByID(getTemplatePicker()
                                            .getSelectedLayer().getID()))
                                    .getFeature(gdbFeatureSelectedId);
                            if (gdbFeatureSelected.getGeometry().getType()
                                    .equals(Geometry.Type.POINT)) {
                                Point pt = (Point) gdbFeatureSelected
                                        .getGeometry();

                                Graphic g = new Graphic(pt,
                                        new SimpleMarkerSymbol(Color.CYAN, 10,
                                                SimpleMarkerSymbol.STYLE.DIAMOND));
                                highlightGraphics.addGraphic(g);
                                popup = new PopupForEditOffline(mMapView,
                                        MainActivity.this);
                                popup.showPopup(e.getX(), e.getY(), 25);
                            } else if (gdbFeatureSelected.getGeometry()
                                    .getType().equals(Geometry.Type.POLYLINE)) {
                                Polyline poly = (Polyline) gdbFeatureSelected
                                        .getGeometry();
                                Graphic g = new Graphic(poly,
                                        new SimpleLineSymbol(Color.CYAN, 5));
                                highlightGraphics.addGraphic(g);
                                popup = new PopupForEditOffline(mMapView,
                                        MainActivity.this);
                                popup.showPopup(e.getX(), e.getY(), 25);

                            } else if (gdbFeatureSelected.getGeometry()
                                    .getType().equals(Geometry.Type.POLYGON)) {
                                Polygon polygon = (Polygon) gdbFeatureSelected
                                        .getGeometry();
                                Graphic g = new Graphic(
                                        polygon,
                                        new SimpleFillSymbol(
                                                Color.CYAN,
                                                com.esri.core.symbol.SimpleFillSymbol.STYLE.SOLID));
                                highlightGraphics.addGraphic(g);
                                popup = new PopupForEditOffline(mMapView,
                                        MainActivity.this);
                                popup.showPopup(e.getX(), e.getY(), 25);

                            }
                        }

                    }
                }
                // }

            }
        }

        @Override
        public boolean onDragPointerMove(MotionEvent from, final MotionEvent to) {
            if (tp != null) {
                if (getTemplatePicker().getselectedTemplate() != null) {
                    setEditingMode();
                }
            }
            return super.onDragPointerMove(from, to);
        }

        @Override
        public boolean onDragPointerUp(MotionEvent from, final MotionEvent to) {
            if (tp != null) {
                if (getTemplatePicker().getselectedTemplate() != null) {
                    setEditingMode();
                }
            }

            return super.onDragPointerUp(from, to);
        }

        /**
         * In this method we check if the point clicked on the map denotes a new
         * point or means an existing vertex must be moved.
         */
        @Override
        public boolean onSingleTap(final MotionEvent e) {
            if (tp != null) {

                Point point = map.toMapPoint(new Point(e.getX(), e.getY()));
                if (getTemplatePicker().getselectedTemplate() != null) {
                    setEditingMode();

                }
                if (getTemplatePicker().getSelectedLayer() != null) {
                    long[] featureIds = ((FeatureLayer) mMapView
                            .getLayerByID(getTemplatePicker()
                                    .getSelectedLayer().getID()))
                            .getFeatureIDs(e.getX(), e.getY(), 25);
                    if (featureIds.length > 0 && (!featureUpdate)) {
                        featureUpdateId = featureIds[0];
                        GdbFeature gdbFeatureSelected = (GdbFeature) ((FeatureLayer) mMapView
                                .getLayerByID(getTemplatePicker()
                                        .getSelectedLayer().getID()))
                                .getFeature(featureIds[0]);
                        if (editingmode == POLYLINE || editingmode == POLYGON) {
                            if (gdbFeatureSelected.getGeometry().getType()
                                    .equals(Geometry.Type.POLYLINE)) {
                                Polyline polyline = (Polyline) gdbFeatureSelected
                                        .getGeometry();
                                for (int i = 0; i < polyline.getPointCount(); i++) {
                                    points.add(polyline.getPoint(i));
                                }
								/*
								 * drawVertices(); drawMidPoints();
								 * drawPolyline();
								 */
                                refresh();

                                editingstates.add(new EditingStates(points,
                                        midpointselected, vertexselected,
                                        insertingindex));

                            } else if (gdbFeatureSelected.getGeometry()
                                    .getType().equals(Geometry.Type.POLYGON)) {
                                Polygon polygon = (Polygon) gdbFeatureSelected
                                        .getGeometry();
                                for (int i = 0; i < polygon.getPointCount(); i++) {
                                    points.add(polygon.getPoint(i));
                                }
								/*
								 * drawVertices(); drawMidPoints();
								 * drawPolyline();
								 */
                                refresh();
                                editingstates.add(new EditingStates(points,
                                        midpointselected, vertexselected,
                                        insertingindex));

                            }
                            featureUpdate = true;
                        }
                        // points.clear();
                    } else {
                        if (editingmode == POINT) {

                            GdbFeature g;
                            try {
                                graphicsLayer.removeAll();
                                // this needs to tbe created from FeatureLayer
                                // by
                                // passing template
                                g = ((GdbFeatureTable) ((FeatureLayer) mMapView
                                        .getLayerByID(getTemplatePicker()
                                                .getSelectedLayer().getID()))
                                        .getFeatureTable())
                                        .createFeatureWithTemplate(
                                                getTemplatePicker()
                                                        .getselectedTemplate(),
                                                point);
                                Symbol symbol = ((FeatureLayer) mMapView
                                        .getLayerByID(getTemplatePicker()
                                                .getSelectedLayer().getID()))
                                        .getRenderer().getSymbol(g);

                                Graphic gr = new Graphic(g.getGeometry(),
                                        symbol, g.getAttributes());

                                addedGraphicId = graphicsLayer.addGraphic(gr);
                            } catch (TableException e1) {
                                // TODO Auto-generated catch block
                                e1.printStackTrace();
                            }

                            points.clear();
                        }
                        if (!midpointselected && !vertexselected) {
                            // check if user tries to select an existing point.

                            int idx1 = getSelectedIndex(e.getX(), e.getY(),
                                    mpoints, map);
                            if (idx1 != -1) {
                                midpointselected = true;
                                insertingindex = idx1;
                            }

                            if (!midpointselected) { // check vertices
                                int idx2 = getSelectedIndex(e.getX(), e.getY(),
                                        points, map);
                                if (idx2 != -1) {
                                    vertexselected = true;
                                    insertingindex = idx2;
                                }

                            }
                            if (!midpointselected && !vertexselected) {
                                // no match, add new vertex at the location
                                points.add(point);
                                editingstates.add(new EditingStates(points,
                                        midpointselected, vertexselected,
                                        insertingindex));
                            }

                        } else if (midpointselected || vertexselected) {
                            int idx1 = getSelectedIndex(e.getX(), e.getY(),
                                    mpoints, map);
                            int idx2 = getSelectedIndex(e.getX(), e.getY(),
                                    points, map);
                            if (idx1 == -1 && idx2 == -1) {
                                movePoint(point);
                                editingstates.add(new EditingStates(points,
                                        midpointselected, vertexselected,
                                        insertingindex));
                            } else {

                                if (idx1 != -1) {
                                    insertingindex = idx1;
                                }
                                if (idx2 != -1) {
                                    insertingindex = idx2;
                                }

                                editingstates.add(new EditingStates(points,
                                        midpointselected, vertexselected,
                                        insertingindex));

                            }
                        } else { // an existing point has been selected
                            // previously.

                            movePoint(point);

                        }
                        refresh();
                        redrawCache = true;
                        return true;
                    }
                }

            }
            return true;
        }

    }


    private class TemplatePickerTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog progressDialog;

        @Override
        protected Void doInBackground(Void... params) {

            // TODO Auto-generated method stub
            setTemplatePicker(new TemplatePicker(MainActivity.this,
                    mMapView));
            return null;
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            progressDialog = ProgressDialog
                    .show(MainActivity.this,
                            "Loading Edit Templates",
                            "Might take more time for layers with many templates",
                            true);
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void result) {
            // TODO Auto-generated method stub
            progressDialog.dismiss();
            getTemplatePicker()
                    .showAtLocation(mTabHost, Gravity.BOTTOM, 0, 0);
            //getTemplatePicker().sh;

            super.onPostExecute(result);
        }

    }

    public TemplatePicker getTemplatePicker() {
        return tp;
    }

    public void setTemplatePicker(TemplatePicker tp) {
        this.tp = tp;
    }

    private void setEditingMode() {
        if (getTemplatePicker() != null) {
            if (getTemplatePicker().getSelectedLayer().getGeometryType()
                    .equals(Geometry.Type.POINT)
                    || getTemplatePicker().getSelectedLayer().getGeometryType()
                    .equals(Geometry.Type.MULTIPOINT)) {
                editingmode = POINT;
                template = getTemplatePicker().getselectedTemplate();
            } else if (getTemplatePicker().getSelectedLayer().getGeometryType()
                    .equals(Geometry.Type.POLYLINE)) {
                editingmode = POLYLINE;
                template = getTemplatePicker().getselectedTemplate();
            } else if (getTemplatePicker().getSelectedLayer().getGeometryType()
                    .equals(Geometry.Type.POLYGON)) {
                editingmode = POLYGON;
                template = getTemplatePicker().getselectedTemplate();
            }
        }
    }

    class EditingStates {
        ArrayList<Point> points1 = new ArrayList<Point>();
        boolean midpointselected1 = false;
        boolean vertexselected1 = false;
        int insertingindex1;

        public EditingStates(ArrayList<Point> points, boolean midpointselected,
                             boolean vertexselected, int insertingindex) {
            this.points1.addAll(points);
            this.midpointselected1 = midpointselected;
            this.vertexselected1 = vertexselected;
            this.insertingindex1 = insertingindex;
        }
    }

    void refresh() {

        if (editingmode != POINT) {
            if (graphicsLayerEditing != null && graphicsLayer != null) {
                graphicsLayerEditing.removeAll();
                graphicsLayer.removeAll();
            }

            drawPolyline();
            drawMidPoints();
            drawVertices();

            undoButton.setEnabled(editingstates.size() > 1);
        }

        clearButton.setEnabled(true);
        removeButton.setEnabled(points.size() > 1 && !midpointselected);
        cancelButton.setEnabled(midpointselected || vertexselected);

        saveButton.setEnabled((editingmode == POINT && points.size() > 0)
                || (editingmode == POLYLINE && points.size() > 1)
                || (editingmode == POLYGON && points.size() > 2));
    }

    private void drawMidPoints() {
        int index;
        Graphic graphic;
        // GraphicsLayer gll = null;
        if (graphicsLayerEditing == null) {
            graphicsLayerEditing = new GraphicsLayer();
            mMapView.addLayer(graphicsLayerEditing);
        }
        // draw mid-point
        if (points.size() > 1) {
            mpoints.clear();
            for (int i = 1; i < points.size(); i++) {
                Point p1 = points.get(i - 1);
                Point p2 = points.get(i);
                mpoints.add(new Point((p1.getX() + p2.getX()) / 2,
                        (p1.getY() + p2.getY()) / 2));
            }
            if (editingmode == POLYGON) { // complete the circle
                Point p1 = points.get(0);
                Point p2 = points.get(points.size() - 1);
                mpoints.add(new Point((p1.getX() + p2.getX()) / 2,
                        (p1.getY() + p2.getY()) / 2));
            }
            index = 0;
            for (Point pt : mpoints) {

                if (midpointselected && insertingindex == index)
                    graphic = new Graphic(pt, new SimpleMarkerSymbol(Color.RED,
                            20, SimpleMarkerSymbol.STYLE.CIRCLE));
                else
                    graphic = new Graphic(pt, new SimpleMarkerSymbol(
                            Color.GREEN, 15, SimpleMarkerSymbol.STYLE.CIRCLE));
                graphicsLayerEditing.addGraphic(graphic);
                index++;
            }
        }
    }

    private void drawVertices() {
        int index;
        // draw vertices
        index = 0;

        if (graphicsLayerEditing == null) {
            graphicsLayerEditing = new GraphicsLayer();
            mMapView.addLayer(graphicsLayerEditing);
        }

        for (Point pt : points) {
            if (vertexselected && index == insertingindex) {
                Graphic graphic = new Graphic(pt, new SimpleMarkerSymbol(
                        Color.RED, 20, SimpleMarkerSymbol.STYLE.CIRCLE));
                Log.d(TAG, "Add Graphic vertex");
                graphicsLayerEditing.addGraphic(graphic);
            } else if (index == points.size() - 1 && !midpointselected
                    && !vertexselected) {
                Graphic graphic = new Graphic(pt, new SimpleMarkerSymbol(
                        Color.RED, 20, SimpleMarkerSymbol.STYLE.CIRCLE));

                int id = graphicsLayer.addGraphic(graphic);

                Log.d(TAG,
                        "Add Graphic mid point" + pt.getX() + " " + pt.getY()
                                + " id = " + id);

            } else {
                Graphic graphic = new Graphic(pt, new SimpleMarkerSymbol(
                        Color.BLACK, 20, SimpleMarkerSymbol.STYLE.CIRCLE));
                Log.d(TAG, "Add Graphic point");
                graphicsLayerEditing.addGraphic(graphic);
            }

            index++;
        }
    }

    private void drawPolyline() {

        if (graphicsLayerEditing == null) {
            graphicsLayerEditing = new GraphicsLayer();
            mMapView.addLayer(graphicsLayerEditing);
        }
        if (points.size() <= 1)
            return;
        Graphic graphic;
        MultiPath multipath;
        if (editingmode == POLYLINE)
            multipath = new Polyline();
        else
            multipath = new Polygon();
        multipath.startPath(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            multipath.lineTo(points.get(i));
        }
        Log.d(TAG, "DrawPolyline: Array coutn = " + points.size());
        if (editingmode == POLYLINE)
            graphic = new Graphic(multipath, new SimpleLineSymbol(Color.BLACK,
                    4));
        else {
            SimpleFillSymbol simpleFillSymbol = new SimpleFillSymbol(
                    Color.YELLOW);
            simpleFillSymbol.setAlpha(100);
            simpleFillSymbol.setOutline(new SimpleLineSymbol(Color.BLACK, 4));
            graphic = new Graphic(multipath, (simpleFillSymbol));
        }
        Log.d(TAG, "Add Graphic Line in DrawPolyline");
        graphicsLayerEditing.addGraphic(graphic);
    }

    int getSelectedIndex(double x, double y, ArrayList<Point> points1,
                         MapView map) {

        if (points1 == null || points1.size() == 0)
            return -1;

        int index = -1;
        double distSQ_Small = Double.MAX_VALUE;
        for (int i = 0; i < points1.size(); i++) {
            Point p = map.toScreenPoint(points1.get(i));
            double diffx = p.getX() - x;
            double diffy = p.getY() - y;
            double distSQ = diffx * diffx + diffy * diffy;
            if (distSQ < distSQ_Small) {
                index = i;
                distSQ_Small = distSQ;
            }
        }

        if (distSQ_Small < (40 * 40)) {
            return index;
        }
        return -1;

    }// end of method

    void movePoint(Point point) {

        if (midpointselected) {// Move mid-point to the new location and make it
            // a vertex.
            points.add(insertingindex + 1, point);
            editingstates.add(new EditingStates(points, midpointselected,
                    vertexselected, insertingindex));
        } else if (vertexselected) {
            ArrayList<Point> temp = new ArrayList<Point>();
            for (int i = 0; i < points.size(); i++) {
                if (i == insertingindex)
                    temp.add(point);
                else
                    temp.add(points.get(i));
            }
            points.clear();
            points.addAll(temp);
            editingstates.add(new EditingStates(points, midpointselected,
                    vertexselected, insertingindex));
        }
        midpointselected = false; // back to the normal drawing mode.
        vertexselected = false;

    }

    private static void syncGeodatabase(final MainActivity activity) {

        try {
            // Create local geodatabase
            Geodatabase gdb = new Geodatabase(gdbFileName);

            // Get sync parameters from geodatabase
            final SyncGeodatabaseParameters syncParams = gdb
                    .getSyncParameters();

            CallbackListener<Geodatabase> syncResponseCallback = new CallbackListener<Geodatabase>() {

                @Override
                public void onCallback(Geodatabase objs) {
                    showMessage(activity, "Sync Completed");
                    showProgress(activity, false);
                    Log.e(TAG, "Geodatabase: " + objs.getPath());
                }

                @Override
                public void onError(Throwable e) {
                    Log.e(TAG, "", e);
                    showMessage(activity, e.getMessage());
                    showProgress(activity, false);
                }

            };
            GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {

                @Override
                public void statusUpdated(GeodatabaseStatusInfo status) {

                    showMessage(activity, status.getStatus().toString());
                }
            };

            // start sync...
            gdbTask.submitSyncJobAndApplyResults(syncParams, gdb,
                    statusCallback, syncResponseCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
