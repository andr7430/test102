package com.esri.android.test102;


/* Copyright 2013 ESRI
 *
 * All rights reserved under the copyright laws of the United States
 * and applicable international laws, treaties, and conventions.
 *
 * You may freely redistribute and use this sample code, with or
 * without modification, provided you include the original copyright
 * notice and use restrictions.
 *
 * See the sample code usage restrictions document for further information.
 *
 */

        import java.util.ArrayList;
        import java.util.Iterator;
        import java.util.List;
        import java.util.Map;
        import java.util.Map.Entry;
        import java.util.concurrent.ExecutionException;
        import java.util.concurrent.atomic.AtomicInteger;

        import android.app.Activity;
        import android.app.Dialog;
        import android.app.ProgressDialog;
        import android.content.Context;
        import android.content.Intent;
        import android.graphics.Color;
        import android.net.Uri;
        import android.os.AsyncTask;
        import android.os.Bundle;
        import android.provider.MediaStore;
        import android.util.Log;
        import android.view.Gravity;
        import android.view.View;
        import android.view.View.OnClickListener;
        import android.view.ViewGroup.LayoutParams;
        import android.widget.Button;
        import android.widget.LinearLayout;
        import android.widget.TextView;

        import com.esri.android.map.FeatureLayer;
        import com.esri.android.map.Layer;
        import com.esri.android.map.MapView;
        import com.esri.android.map.ags.ArcGISPopupInfo;
        import com.esri.android.map.popup.Popup;
        import com.esri.android.map.popup.PopupContainer;
        import com.esri.core.gdb.GdbFeature;
        import com.esri.core.gdb.GdbFeatureTable;
        import com.esri.core.gdb.Relationship;
        import com.esri.core.map.Feature;
        import com.esri.core.map.FeatureResult;
        import com.esri.core.map.Field;
        import com.esri.core.map.Graphic;
        import com.esri.core.table.TableException;
        import com.esri.core.tasks.query.RelatedQueryParameters;

public class PopupForEditOffline {

    private MapView map;
    private Activity mainActivity;

    private PopupContainer popupContainer;
    private PopupContainer relPopupContainer;
    private PopupDialog popupDialog;
    private PopupDialog relpopupDialog;
    private ProgressDialog progressDialog;
    private AtomicInteger count;
    private LinearLayout editorBarLocal;
    private LinearLayout editorBarLocalRel;
    private GdbFeature selectedGdbFeature;
    private boolean isRelatedPopupContainerOpen = false;

    private int counter = 0;

//	private final String TAG = "PopupForEditOffline";

    public PopupForEditOffline(MapView mapView, Activity mainActivity) {
        this.map = mapView;
        this.mainActivity = mainActivity;
    }

    public void addAttachment(Uri selectedImage) {
        if (!isRelatedPopupContainerOpen) {
            Popup popup = popupContainer.getCurrentPopup();
            if (popup != null)
                popup.addAttachment(selectedImage);
        } else {
            Popup popup = relPopupContainer.getCurrentPopup();
            if (popup != null)
                popup.addAttachment(selectedImage);
        }

    }

    public void showPopup(float x, float y, int tolerance) {
        if (!map.isLoaded())
            return;

        // Instantiate a PopupContainer
        popupContainer = new PopupContainer(map);
        int id = popupContainer.hashCode();
        popupDialog = null;
        // Display spinner.
        if (progressDialog == null || !progressDialog.isShowing())
            progressDialog = ProgressDialog.show(map.getContext(), "",
                    "Querying...");

        // Loop through each layer in the webmap
//		Envelope env = new Envelope(map.toMapPoint(x, y), tolerance
//				* map.getResolution(), tolerance * map.getResolution());
        Layer[] layers = map.getLayers();
        count = new AtomicInteger();
        for (Layer layer : layers) {
            // If the layer has not been initialized or is invisible, do
            // nothing.
            if (!layer.isInitialized() || !layer.isVisible())
                continue;

            if (layer instanceof FeatureLayer) {
                // Query feature layer and display popups
                FeatureLayer localFeatureLayer = (FeatureLayer) layer;
                if (localFeatureLayer.getPopupInfos() != null) {
                    // Query feature layer which is associated with a popup
                    // definition.
                    count.incrementAndGet();
                    new RunQueryLocalFeatureLayerTask(x, y, tolerance, id)
                            .execute(localFeatureLayer);
                }
            }
        }

    }

    private class RunQueryLocalFeatureLayerTask extends
            AsyncTask<FeatureLayer, Void, Feature[]> {

        private int tolerance;
        private float x;
        private float y;
        private FeatureLayer localFeatureLayer;
        private int id;

        public RunQueryLocalFeatureLayerTask(float x, float y, int tolerance,
                                             int id) {
            super();
            this.x = x;
            this.y = y;
            this.tolerance = tolerance;
            this.id = id;
        }

        @Override
        protected Feature[] doInBackground(FeatureLayer... params) {
            for (FeatureLayer featureLayer : params) {
                this.localFeatureLayer = featureLayer;
                // Retrieve graphic ids near the point.
                long[] ids = featureLayer.getFeatureIDs(x, y, tolerance);
                if (ids != null && ids.length > 0) {
                    ArrayList<Feature> features = new ArrayList<Feature>();
                    for (long id : ids) {
                        // Obtain graphic based on the id.

                        Feature f = featureLayer.getFeature(id);
                        if (f == null)
                            continue;
                        features.add(f);
                    }
                    // Return an array of graphics near the point.
                    return features.toArray(new Feature[0]);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Feature[] features) {
            count.decrementAndGet();
            // Validate parameter.
            if (features == null || features.length == 0) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }
            // Check if the requested PopupContainer id is the same as the
            // current PopupContainer.
            // Otherwise, abandon the obsoleted query result.
            if (id != popupContainer.hashCode()) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }

            Map<Integer, ArcGISPopupInfo> popupInfos = localFeatureLayer
                    .getPopupInfos();
            if (popupInfos == null) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }

            for (Feature fr : features) {
                Popup popup = localFeatureLayer.createPopup(map, 0, fr);
                popup.setEditable(true);
                popupContainer.addPopup(popup);
            }
            counter++;
            if (counter < 2) {
                createEditorBarLocal(localFeatureLayer, true);

            }
            createPopupViews(id);
        }

    }

    private void createEditorBarLocal(final FeatureLayer fl,
                                      final boolean existing) {

        if (fl == null || !fl.isInitialized()
                || !fl.getFeatureTable().isEditable())
            return;

        editorBarLocal = new LinearLayout(mainActivity);

        Button cancelButton = new Button(mainActivity);
        cancelButton.setText("Cancel");
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupDialog != null)
                    popupDialog.dismiss();
            }
        });
        editorBarLocal.addView(cancelButton);

        final Button deleteButton = new Button(mainActivity);
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupContainer == null
                        || popupContainer.getPopupCount() <= 0)
                    return;
                popupDialog.dismiss();

                GdbFeature fr = (GdbFeature) popupContainer.getCurrentPopup()
                        .getFeature();

                try {
                    long deleteId = fr.getId();
                    fr.getTable().deleteFeature(deleteId);
                } catch (TableException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });
        if (existing)
            editorBarLocal.addView(deleteButton);

        final Button attachmentButton = new Button(mainActivity);
        attachmentButton.setText("Add ATCH");

        attachmentButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setSelectedGdbFeature((GdbFeature) popupContainer
                        .getCurrentPopup().getFeature());
                mainActivity.startActivityForResult(new Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI), 3);
            }
        });
        if (!existing
                && ((GdbFeatureTable) ((GdbFeature) popupContainer
                .getCurrentPopup().getFeature()).getTable())
                .hasAttachments())
            attachmentButton.setVisibility(View.VISIBLE);
        else
            attachmentButton.setVisibility(View.INVISIBLE);
        editorBarLocal.addView(attachmentButton);

        // Related Records
        final List<Relationship> relations = ((GdbFeatureTable) ((GdbFeature) popupContainer
                .getCurrentPopup().getFeature()).getTable()).getRelationships();
        final Button relationButton = new Button(mainActivity);
        relationButton.setText("REL");
        relationButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Popup popup = popupContainer.getCurrentPopup();
                int relCount = 0;
                GdbFeature gdbFeature = (GdbFeature) popup.getFeature();
                Log.d("PopupForEditOffline - Feature Object ID",
                        String.valueOf(gdbFeature.getId()));
                Log.d("PopupForEditOffline - Feature TableName", gdbFeature
                        .getTable().getTableName());

                relPopupContainer = new PopupContainer(map);

                for (Relationship relationship : relations) {
                    long[] objectIds = new long[1];
                    objectIds[0] = gdbFeature.getId();
                    GdbFeatureTable relatedTable = relationship
                            .getRelatedTable();
                    RelatedQueryParameters relatedQuery = new RelatedQueryParameters();
                    relatedQuery.setRelationshipId(relationship.getId());
                    relatedQuery.setObjectIds(objectIds);
                    Log.d("PopupForEditOffline - ObjectId",
                            objectIds.toString());
                    try {

                        Map<Long, FeatureResult> relatedFeatures = ((GdbFeatureTable) gdbFeature
                                .getTable()).queryRelated(relatedQuery, null)
                                .get();
                        Log.d("PopupForEditOffline", relatedFeatures.toString());
                        for (Map.Entry<Long, FeatureResult> entry : relatedFeatures
                                .entrySet()) {
                            Long Key = entry.getKey();
                            FeatureResult fResults = entry.getValue();
                            Iterator<Object> fResult = fResults.iterator();
                            if (fResult.hasNext()) {
                                while (fResult.hasNext()) {
                                    GdbFeature relGdbFeature = (GdbFeature) fResult
                                            .next();
                                    Log.d("PopupForEditOffline - Related Feature Object ID",
                                            String.valueOf(relGdbFeature
                                                    .getId()));
                                    Log.d("PopupForEditOffline - Related Feature TableName",
                                            relGdbFeature.getTable()
                                                    .getTableName());
                                    Log.d("PopupForEditOffline - Related Feature TableName-Actual",
                                            relatedTable.getTableName());

                                    FeatureLayer relLayer = new FeatureLayer(
                                            relGdbFeature.getTable());
                                    Popup relPopup = relLayer.createPopup(map,
                                            0, relGdbFeature);
                                    relPopupContainer.addPopup(relPopup);
                                    Log.d("PopupForEditOffline", "Long Key"
                                            + Key
                                            + "Rel ObjectID"
                                            + relGdbFeature.getId()
                                            + "Rel - Attributes"
                                            + relGdbFeature.getAttributes()
                                            .toString());
                                }
                                relCount++;
                                if (relCount < 2) {
                                    createEditorBarLocalRel(new FeatureLayer(
                                            gdbFeature.getTable()), true);
                                }

                            } else {

                                TextView noRecords = new TextView(mainActivity);
                                noRecords
                                        .setText("No Related Records for this Feature");
                                noRecords.setBackgroundColor(Color.WHITE);
                                noRecords.setGravity(Gravity.CENTER);
                                noRecords.setTextColor(Color.MAGENTA);
                                relPopupContainer.getPopupContainerView()
                                        .addView(noRecords);
                            }

                        }

                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
                // createPopupViewsRel(idRel);
                relpopupDialog = new PopupDialog(map.getContext(),
                        relPopupContainer);
                relpopupDialog.show();
                isRelatedPopupContainerOpen = true;
            }
        });

        if (relations.size() > 0) {
            relationButton.setVisibility(View.VISIBLE);
        } else {
            relationButton.setVisibility(View.INVISIBLE);
        }
        editorBarLocal.addView(relationButton);

        // end RelatedRecords
        final Button saveButton = new Button(mainActivity);
        saveButton.setText("Save");
        if (existing)
            saveButton.setVisibility(View.INVISIBLE);
        saveButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupContainer == null
                        || popupContainer.getPopupCount() <= 0)
                    return;
                popupDialog.dismiss();

                Popup popup = popupContainer.getCurrentPopup();
                GdbFeature fr = (GdbFeature) popup.getFeature();

                Map<String, Object> attributes = fr.getAttributes();
                Map<String, Object> updatedAttrs = popup.getUpdatedAttributes();

                Iterator<Map.Entry<String, Object>> iter = attributes
                        .entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, Object> entry = iter.next();
                    Field f = fr.getTable().getField(entry.getKey());
                    if (!f.isEditable()) {
                        if (entry.getKey().equals("CREATED_USER")
                                || entry.getKey().equals("CREATED_DATE")
                                || entry.getKey().equals("LAST_EDITED_USER")
                                || entry.getKey().equals("LAST_EDITED_DATE")) {
                            iter.remove();
                        }
                    }

                }

                for (Entry<String, Object> entry : updatedAttrs.entrySet()) {

                    attributes.put(entry.getKey(), entry.getValue());

                }

                Graphic newgr = new Graphic(fr.getGeometry(), null, attributes);

                if (existing)
                    try {
                        long updateId = fr.getId();
                        Log.d("PopupEditForOffline", newgr.getAttributes()
                                .toString());
                        fr.getTable().updateFeature(updateId, newgr);

                    } catch (TableException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                else
                    try {
                        fr.getTable().addFeature(newgr);
                    } catch (TableException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

            }
        });
        editorBarLocal.addView(saveButton);

        final Button editButton = new Button(map.getContext());
        editButton.setText("Edit");
        editButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupContainer == null
                        || popupContainer.getPopupCount() <= 0)
                    return;

                popupContainer.getCurrentPopup().setEditMode(true);

                saveButton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.INVISIBLE);
                editButton.setVisibility(View.INVISIBLE);
                if (((GdbFeatureTable) ((GdbFeature) popupContainer
                        .getCurrentPopup().getFeature()).getTable())
                        .hasAttachments())
                    attachmentButton.setVisibility(View.VISIBLE);
            }
        });
        if (existing) {
            editorBarLocal.addView(editButton);
        }

        popupContainer.getPopupContainerView().addView(editorBarLocal, 0);

    }

    private void createPopupViews(final int id) {
        if (id != popupContainer.hashCode()) {
            if (progressDialog != null && progressDialog.isShowing()
                    && count.intValue() == 0)
                progressDialog.dismiss();

            return;
        }

        if (popupDialog == null) {
            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();

            // Create a dialog for the popups and display it.
            popupDialog = new PopupDialog(map.getContext(), popupContainer);
            popupDialog.show();
        }
    }

    // A customize full screen dialog.
    private class PopupDialog extends Dialog {
        private PopupContainer popupContainer;

        public PopupDialog(Context context, PopupContainer popupContainer) {
            super(context, android.R.style.Theme);
            this.popupContainer = popupContainer;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);
            LinearLayout layout = new LinearLayout(getContext());
            layout.addView(popupContainer.getPopupContainerView(),
                    android.widget.LinearLayout.LayoutParams.FILL_PARENT,
                    android.widget.LinearLayout.LayoutParams.FILL_PARENT);
            setContentView(layout, params);
        }

    }

    private void createEditorBarLocalRel(final FeatureLayer fl,
                                         final boolean existing) {

        if (fl == null || !fl.isInitialized()
                || !fl.getFeatureTable().isEditable())
            return;

        LinearLayout relLayout = new LinearLayout(mainActivity);
        relLayout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));
        relLayout.setOrientation(LinearLayout.VERTICAL);

        TextView textHeader = new TextView(mainActivity);
        textHeader.setText("Related Records: OID: "
                + String.valueOf(popupContainer.getCurrentPopup().getFeature()
                .getId())
                + " Table: "
                + ((GdbFeature) popupContainer.getCurrentPopup().getFeature())
                .getTable().getTableName());
        textHeader.setGravity(Gravity.CENTER);
        textHeader.setTextColor(Color.MAGENTA);
        textHeader.setId(1);

        editorBarLocalRel = new LinearLayout(mainActivity);

        Button cancelButton = new Button(mainActivity);
        cancelButton.setText("Cancel");
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                isRelatedPopupContainerOpen = false;
                if (relpopupDialog != null)
                    relpopupDialog.dismiss();
            }
        });
        editorBarLocalRel.addView(cancelButton);

        final Button deleteButton = new Button(mainActivity);
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                isRelatedPopupContainerOpen = false;
                if (relPopupContainer == null
                        || relPopupContainer.getPopupCount() <= 0)
                    return;
                relpopupDialog.dismiss();

                GdbFeature fr = (GdbFeature) relPopupContainer
                        .getCurrentPopup().getFeature();

                try {
                    long deleteId = fr.getId();
                    fr.getTable().deleteFeature(deleteId);
                } catch (TableException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        });
        if (existing)
            editorBarLocalRel.addView(deleteButton);

        final Button attachmentButton = new Button(mainActivity);
        attachmentButton.setText("Add ATCH");

        attachmentButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setSelectedGdbFeature((GdbFeature) relPopupContainer
                        .getCurrentPopup().getFeature());
                mainActivity.startActivityForResult(new Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI), 3);
            }
        });
        if (!existing
                && ((GdbFeatureTable) ((GdbFeature) relPopupContainer
                .getCurrentPopup().getFeature()).getTable())
                .hasAttachments())
            attachmentButton.setVisibility(View.VISIBLE);
        else
            attachmentButton.setVisibility(View.INVISIBLE);
        editorBarLocalRel.addView(attachmentButton);

        final Button saveButton = new Button(mainActivity);
        saveButton.setText("Save");
        if (existing)
            saveButton.setVisibility(View.INVISIBLE);
        saveButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                isRelatedPopupContainerOpen = false;
                if (relPopupContainer == null
                        || relPopupContainer.getPopupCount() <= 0)
                    return;
                relpopupDialog.dismiss();

                Popup popup = relPopupContainer.getCurrentPopup();
                GdbFeature fr = (GdbFeature) popup.getFeature();

                Map<String, Object> attributes = fr.getAttributes();
                Map<String, Object> updatedAttrs = popup.getUpdatedAttributes();
                for (Entry<String, Object> entry : updatedAttrs.entrySet()) {

                    attributes.put(entry.getKey(), entry.getValue());

                }

                Graphic newgr = new Graphic(fr.getGeometry(), null, attributes);

                if (existing)
                    try {
                        long updateId = fr.getId();
                        Log.d("PopupEditForOffline", newgr.getAttributes()
                                .toString());
                        fr.getTable().updateFeature(updateId, newgr);
                    } catch (TableException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                else
                    try {
                        fr.getTable().addFeature(newgr);
                    } catch (TableException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

            }
        });
        editorBarLocalRel.addView(saveButton);

        final Button editButton = new Button(map.getContext());
        editButton.setText("Edit");
        editButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (relPopupContainer == null
                        || relPopupContainer.getPopupCount() <= 0)
                    return;

                relPopupContainer.getCurrentPopup().setEditMode(true);

                saveButton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.INVISIBLE);
                editButton.setVisibility(View.INVISIBLE);
                if (((GdbFeatureTable) ((GdbFeature) relPopupContainer
                        .getCurrentPopup().getFeature()).getTable())
                        .hasAttachments())
                    attachmentButton.setVisibility(View.VISIBLE);
            }
        });
        if (existing) {
            editorBarLocalRel.addView(editButton);
        }
        relLayout.addView(textHeader);
        relLayout.addView(editorBarLocalRel);
        relPopupContainer.getPopupContainerView().addView(relLayout, 0);

    }

    public GdbFeature getSelectedGdbFeature() {
        return selectedGdbFeature;
    }

    public void setSelectedGdbFeature(GdbFeature selectedGdbFeature) {
        this.selectedGdbFeature = selectedGdbFeature;
    }
}
