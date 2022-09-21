package com.example.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.concurrent.ListenableFuture
import com.esri.arcgisruntime.data.FeatureQueryResult
import com.esri.arcgisruntime.data.QueryParameters
import com.esri.arcgisruntime.data.ServiceFeatureTable
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.MapView

import com.example.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val activityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val mapView: MapView by lazy {
        activityMainBinding.mapView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(activityMainBinding.root)

        setApiKeyForApp()

        setupMap()
    }

    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        mapView.dispose()
        super.onDestroy()
    }

    private fun setApiKeyForApp() {
        ArcGISRuntimeEnvironment.setApiKey("YOUR_API_KEY")
    }

    private fun setupMap() {
        val map = ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC)
        mapView.map = map
        mapView.setViewpoint(Viewpoint(43.8971, -78.8658, 72000.0))
        createFeatureLayer(map)
    }

    private fun queryFeatureLayer(layerId: String, whereExpression: String, queryExtent: Envelope) {

        // Get the layer based on its Id.
        lateinit var featureLayerToQuery: FeatureLayer

        mapView.map.operationalLayers.iterator().forEach { layer ->
            if (layer.id == layerId) {
                featureLayerToQuery = layer as FeatureLayer
            }
        }
        // get the feature table from the feature layer
        val featureTableToQuery = featureLayerToQuery.featureTable

        // clear any previous selections
        featureLayerToQuery.clearSelection()
        // create a query for the state that was entered
        val query = QueryParameters().apply {
            // make search case insensitive
            whereClause = whereExpression
            isReturnGeometry = true
            geometry = queryExtent
        }

        // call select feature
        val future: ListenableFuture<FeatureQueryResult> =
            featureTableToQuery.queryFeaturesAsync(query)
        // add done listener to fire when the selection returns
        future.addDoneListener {
            try {
                // check if there are some results
                val resultIterator = future.get().iterator()

                if (resultIterator.hasNext()) {
                    resultIterator.forEach { feature ->
                        // select the feature
                        featureLayerToQuery.selectFeature(feature)
                    }
                } else {
                    "No parcels found in the current extent, using Where expression: $whereExpression".also {
                        Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                        Log.d(TAG, it)
                    }
                }
            } catch (e: Exception) {
                "Feature search failed for: $whereExpression. Error: ${e.message}".also {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    Log.e(TAG, it)
                }
            }
        }

    }
    
    private fun createFeatureLayer(map: ArcGISMap) {

        val serviceFeatureTable =
            ServiceFeatureTable("https://services3.arcgis.com/R1QgHoeCpv6vXgCd/ArcGIS/rest/services/emergency_areas/FeatureServer/0")

        val parcelsFeatureLayer = FeatureLayer(serviceFeatureTable)

        // give the layer an ID so we can easily find it later, then add it to the map
        parcelsFeatureLayer.id = "Parcels"

        parcelsFeatureLayer.loadAsync()
        parcelsFeatureLayer.addDoneLoadingListener {
            val spinner = activityMainBinding.spinner
            // set up the spinner with the parcel_categories string-array in strings.xml
            ArrayAdapter.createFromResource(
                this,
                R.array.parcel_categories,
                android.R.layout.simple_spinner_item
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position == 0) {
                        parcelsFeatureLayer.clearSelection()
                        return
                    }
                    val currentSpinnerChoice: String = parent.getItemAtPosition(position) as String
                    val currentExtent: Envelope =
                        mapView.getCurrentViewpoint(Viewpoint.Type.BOUNDING_GEOMETRY).targetGeometry as Envelope

                    queryFeatureLayer("Parcels", currentSpinnerChoice, currentExtent)
                }
            }

        }

        map.operationalLayers.add(parcelsFeatureLayer)

        // get selection properties (bound to the MapView)
        mapView.selectionProperties.color = Color.RED

    }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
    }
}