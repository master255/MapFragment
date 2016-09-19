package me.drnear.ui.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import me.drnear.R;
import me.drnear.event.DoctorSubscribedEvent;
import me.drnear.event.MyLocationChangedEvent;
import me.drnear.rest.model.common.SearchLocation;
import me.drnear.rest.model.search.SearchObjectDTO;
import me.drnear.rest.model.search.SearchRequestDTO;
import me.drnear.rest.model.social.GroupDTO;
import me.drnear.rest.model.user.FollowEnum;
import me.drnear.rest.model.user.Practice2;
import me.drnear.rest.model.user.ProfileCardDTO;
import me.drnear.ui.auth.LoginFragment2;
import me.drnear.ui.booking.BookingFragment1;
import me.drnear.ui.common.BaseFragment;
import me.drnear.ui.common.content.IHolderClickListener;
import me.drnear.ui.doctor_profile.DoctorProfileFragment2;
import me.drnear.utils.Constants;
import me.drnear.utils.SharedPrefsUtil;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by KazOleg on 27.06.2016.
 */
public class MapFragment extends BaseFragment implements ClusterManager
		.OnClusterClickListener<ProfileCardDTO>,
		ClusterManager.OnClusterItemClickListener<ProfileCardDTO>, IHolderClickListener, GoogleMap.OnCameraChangeListener, GoogleMap.OnMarkerClickListener,
		OnMapReadyCallback
{
	public static final int ANIMATE_DURATION_MS = 800;
	@Bind(R.id.map_layout)
	RelativeLayout map_layout;
	@Bind(R.id.viewpager_cards)
	ViewPager viewPagerDoctorCards;
	@Bind(R.id.bottom_card_layout)
	CardView bottomCardLayout;
	@Bind(R.id.loading_layout)
	RelativeLayout progressBarLayout;
	private DoctorMarkerRenderer doctorMarkerRenderer;
	public static final String MAP_TAG = "map";
	private LatLngBounds initialLatLngBounds;
	private ClusterManager<ProfileCardDTO> clusterManager;
	private ClusterManager<ProfileCardDTO> clusterManagerForSelected;
	private DoctorMarkerRenderer doctorMarkerRendererForSelected;
	private ProfileCardDTO selectedDoctorMiniProfile;
	private ArrayList<ProfileCardDTO> pins;
	private ArrayList<ProfileCardDTO> doctorMiniProfilesWithLocationForMap = new ArrayList<>();
	private ArrayList<ProfileCardDTO> doctorMiniProfilesWithLocation = new ArrayList<>();
	private GoogleMap map;
	protected DoctorCardsPagerAdapter doctorCardsPagerAdapter;
	private List<ProfileCardDTO> inProcessOfSubscribing = new ArrayList<>();
	private int type;
	private float zoomLevel, zoomOld;
	private List<ProfileCardDTO> inProcessOfClick = new ArrayList<>();
	private Cluster<ProfileCardDTO> tmpCluster;
	private Marker clickedMrk, oldClickedMrk;
	private int oldIndex = -1;
	private List<Polyline> polylines = new ArrayList<Polyline>();
	private boolean inUpdate = false;
	SearchLocation lastLocation = null;
	
	public MapFragment()
	{
		// Required empty public constructor
	}
	
	public static MapFragment newInstance(ArrayList<ProfileCardDTO> dataSet, int type)
	{
		MapFragment fragment = new MapFragment();
		Bundle args = new Bundle();
		args.putSerializable(Constants.Extra.LIST, dataSet);
		args.putInt(Constants.Extra.TYPE, type);
		fragment.setArguments(args);
		return fragment;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null)
		{
			pins = (ArrayList<ProfileCardDTO>) savedInstanceState.getSerializable(Constants.Extra.LIST);
			type = savedInstanceState.getInt(Constants.Extra.TYPE, -1);
		} else
		{
			pins = (ArrayList<ProfileCardDTO>) getArguments().getSerializable(Constants.Extra.LIST);
			type = getArguments().getInt(Constants.Extra.TYPE, -1);
		}
		MapsInitializer.initialize(getActivity());
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putSerializable(Constants.Extra.LIST, pins);
		outState.putInt(Constants.Extra.TYPE, type);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.doctors_map_fragment, container, false);
		ButterKnife.bind(this, view);
		getSingleActivity().setNavigationBarVisible(false);
		doctorMiniProfilesWithLocation.clear();
		addUniqueProfiles(doctorMiniProfilesWithLocation, getProfileWithLocationsList(pins));
		if (doctorMiniProfilesWithLocation.size() < 1)
		{
			back();
		} else
		{
			createGoogleMap();
		}
		return view;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);
		if (isActiveFragment())
		{
			inflater.inflate(R.menu.menu_map, menu);
		}
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		if (isActiveFragment())
		{
			switch (type)
			{
				case 1:
					menu.findItem(R.id.list_view).setVisible(false);
					break;
			}
			MarkerType markerType = doctorMarkerRenderer != null ? doctorMarkerRenderer.getMarkerType() : MarkerType.TIME;
			switch (markerType)
			{
				case AVATAR:
					menu.findItem(R.id.pin_avatar_map).setChecked(true);
					break;
				case SPECIALTY:
					menu.findItem(R.id.pin_specialty_map).setChecked(true);
					break;
				case TIME:
					menu.findItem(R.id.pin_time_map).setChecked(true);
					break;
			}
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.list_view:
				back();
				break;
			case R.id.pin_avatar_map:
				if (item.isChecked()) item.setChecked(false);
				else item.setChecked(true);
				changeRenderMarkerType(MarkerType.AVATAR);
				break;
			case R.id.pin_specialty_map:
				if (item.isChecked()) item.setChecked(false);
				else item.setChecked(true);
				changeRenderMarkerType(MarkerType.SPECIALTY);
				break;
			case R.id.pin_time_map:
				if (item.isChecked()) item.setChecked(false);
				else item.setChecked(true);
				changeRenderMarkerType(MarkerType.TIME);
				break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		SearchLocation tmpLocation = SharedPrefsUtil.loadLocationSharedPrefs(getContext());
		if (lastLocation == null)
		{
			lastLocation = tmpLocation;
		} else
		{
			if (lastLocation != tmpLocation)
			{
				if (isActiveFragment())
				{
					getSingleActivity().showProgressBar();
				}
				if (type == 1)
				{
					SearchRequestDTO searchRequestDTO = new SearchRequestDTO();
					searchRequestDTO.setGeoLocation(tmpLocation);
					SearchObjectDTO searchObjectDTO = new SearchObjectDTO(searchRequestDTO);
					searchAPI.searchDoctors(searchObjectDTO, 40, 0, false)
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(profileCardDTOs -> {
								doctorMiniProfilesWithLocation.clear();
								addUniqueProfiles(doctorMiniProfilesWithLocation, getProfileWithLocationsList(profileCardDTOs));
								addDoctorsToMap();
								selectedDoctorMiniProfile = doctorMiniProfilesWithLocation.get(0);
								changeRenderMarkerType(doctorMarkerRenderer.getMarkerType());
								doctorCardsPagerAdapter.notifyDataSetChanged();
								bringMarkerToTop(selectedDoctorMiniProfile);
								if (isActiveFragment())
								{
									getSingleActivity().hideProgressBar();
									getSingleActivity().setStatusViewVisibility(true);
								}
							}, throwable -> {
								if (isActiveFragment())
								{
									getSingleActivity().hideProgressBar();
									getSingleActivity().setStatusViewVisibility(true);
								}
							});
				} else
				{
					try
					{
						LatLng tmpLatLng = new LatLng(Double.parseDouble(tmpLocation
								.getLatitude()), Double.parseDouble(tmpLocation.getLongitude()));
						getGoogleMap().animateCamera(CameraUpdateFactory.newLatLng(tmpLatLng),
								ANIMATE_DURATION_MS, null);
					} catch (NumberFormatException e)
					{
						e.printStackTrace();
					}
					if (isActiveFragment())
					{
						getSingleActivity().hideProgressBar();
						getSingleActivity().setStatusViewVisibility(true);
					}
				}
				lastLocation = tmpLocation;
			}
		}
		getSingleActivity().setLocationText();
	}
	
	private void changeRenderMarkerType(MarkerType markerType)
	{
		for (Polyline line : polylines)
		{
			line.remove();
		}
		polylines.clear();
		inProcessOfClick.clear();
		clickedMrk = null;
		oldClickedMrk = null;
		resetClusterManagers();
		initializeClusterManager(getGoogleMap(), markerType);
		doctorMiniProfilesWithLocationForMap.clear();
		if (selectedDoctorMiniProfile != null)
		{
			for (ProfileCardDTO profile : doctorMiniProfilesWithLocation)
			{
				if (!profile.getMapKey().equals(selectedDoctorMiniProfile.getMapKey()))
				{
					doctorMiniProfilesWithLocationForMap.add(profile);
				}
			}
		}
		fillClusterManagers();
	}
	
	private void fillClusterManagers()
	{
		if ((clusterManagerForSelected != null) && (selectedDoctorMiniProfile != null))
		{
			clusterManagerForSelected.clearItems();
			clusterManagerForSelected.addItem(selectedDoctorMiniProfile);
			clusterManagerForSelected.cluster();
		}
		if ((clusterManager != null) && (doctorMiniProfilesWithLocationForMap != null))
		{
			clusterManager.clearItems();
			clusterManager.addItems(doctorMiniProfilesWithLocationForMap);
			clusterManager.cluster();
		}
		if (selectedDoctorMiniProfile != null)
			new Handler().postDelayed(() -> bringMarkerToTop(selectedDoctorMiniProfile), 250);
	}
	
	private void resetClusterManagers()
	{
		if (clusterManager != null)
		{
			clusterManager.clearItems();
			clusterManager.setRenderer(doctorMarkerRenderer);
		}
		if (clusterManagerForSelected != null)
		{
			clusterManagerForSelected.clearItems();
			clusterManagerForSelected.setRenderer(doctorMarkerRendererForSelected);
		}
	}
	
	private void bringMarkerToTop(ProfileCardDTO profile)
	{
		if (doctorMarkerRendererForSelected != null)
		{
			Marker marker1 = doctorMarkerRendererForSelected.getMarker(profile);
			if (marker1 != null)
			{
				//Log.d(getLogTag(), "showInfoWindow");
				marker1.showInfoWindow();
			}
		}
	}
	
	private void createGoogleMap()
	{
		//Log.d(getLogTag(), "createGoogleMap: " + initialLatLngBounds);
		GoogleMapOptions googleMapOptions = new GoogleMapOptions();
		if (initialLatLngBounds != null)
		{
			int zoom = MapUtils.getBoundsZoomLevel(initialLatLngBounds, map_layout.getWidth(), map_layout.getHeight() / 2);
			googleMapOptions.camera(CameraPosition.fromLatLngZoom(initialLatLngBounds.getCenter(), zoom));
		}
		googleMapOptions.zoomControlsEnabled(true).mapToolbarEnabled(true);
		SupportMapFragment supportMapFragment = SupportMapFragment.newInstance(googleMapOptions);
		getFragmentManager().beginTransaction().replace(R.id.doctor_map_container,
				supportMapFragment, MAP_TAG).commit();
//        supportMapFragment.getMapAsync(this);
		supportMapFragment.getMapAsync(googleMap -> googleMap.setOnMapLoadedCallback(this::setupMap));
	}
	
	private GoogleMap getGoogleMap()
	{
//        return map;
//        else {
		SupportMapFragment supportMapFragment = getSupportMapFragment();
		return supportMapFragment != null ? supportMapFragment.getMap() : null;
//        }
	}
	
	private SupportMapFragment getSupportMapFragment()
	{
		if (getFragmentManager() != null)
			return (SupportMapFragment) getFragmentManager().findFragmentByTag(MAP_TAG);
		else
			return null;
	}
	
	private void initializeClusterManager(GoogleMap googleMap)
	{
		initializeClusterManager(googleMap, MarkerType.TIME);
	}
	
	private void initializeClusterManager(GoogleMap googleMap, MarkerType markerType)
	{
		//Log.d(getLogTag(), "initializeClusterManager");
		if (googleMap != null)
		{
			clusterManager = new ClusterManager<>(getActivity(), googleMap);
			doctorMarkerRenderer = new DoctorMarkerRenderer(getActivity(), googleMap, clusterManager, picasso, false, markerType);
			clusterManager.setRenderer(doctorMarkerRenderer);
			clusterManager.setAlgorithm(new NonHierarchicalDistanceBasedAlgorithm<>());
			clusterManager.setOnClusterClickListener(this);
			clusterManager.setOnClusterItemClickListener(this);
			clusterManagerForSelected = new ClusterManager<>(getActivity(), googleMap);
			doctorMarkerRendererForSelected = new DoctorMarkerRenderer(getActivity(), googleMap, clusterManagerForSelected, picasso, true, markerType);
			clusterManagerForSelected.setRenderer(doctorMarkerRendererForSelected);
			clusterManagerForSelected.setAlgorithm(new NonHierarchicalDistanceBasedAlgorithm<>());
			clusterManagerForSelected.setOnClusterClickListener(this);
			clusterManagerForSelected.setOnClusterItemClickListener(this);
		}
	}
	
	private List<ProfileCardDTO> getProfileWithLocationsList(List<ProfileCardDTO> source)
	{
		List<ProfileCardDTO> results = new ArrayList<>();
		for (ProfileCardDTO doctorMiniProfile : source)
		{
			Practice2 location = doctorMiniProfile.getLocation();
			if (location != null && location.notEmpty())
			{
				results.add(doctorMiniProfile);
			}
		}
		return results;
	}
	
	/**
	 * Copy unique profiles from FROM to TO list
	 * If profile from RECENT tab - add it to the beginning of list
	 *
	 * @param to   list to add unique profiles to
	 * @param from list to iterate
	 */
	private void addUniqueProfiles(List<ProfileCardDTO> to, List<ProfileCardDTO> from)
	{
		for (ProfileCardDTO profile : from)
		{
			if (!to.contains(profile))
			{
				to.add(profile);
			}
		}
	}
	
	private void addDoctorsToMap()
	{
		LatLngBounds.Builder builder = new LatLngBounds.Builder();
		boolean hasLocations = false;
		for (ProfileCardDTO doctorMiniProfile : getDoctorMiniProfilesWithLocation())
		{
			Practice2 location = doctorMiniProfile.getLocation();
			LatLng latLng = new LatLng(Double.parseDouble(location.getLatitude()), Double.parseDouble(location
					.getLongitude()));
			builder.include(latLng);
			hasLocations = true;
		}
		if (hasLocations)
		{
			initialLatLngBounds = builder.build();
		}
		//Log.d(getLogTag(), "addDoctorsToMap: hasLocations = " + hasLocations);
		bottomCardLayout.setVisibility(hasLocations ? View.VISIBLE : View.GONE);
		if (clusterManager == null) return;
		GoogleMap googleMap = getGoogleMap();
		if (hasLocations)
		{
			// left small animation to first screen appearance
			googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(initialLatLngBounds, getResources().getDimensionPixelSize(R.dimen.map_padding)));
			bringMarkerToTop(selectedDoctorMiniProfile);
			progressBarLayout.setVisibility(View.GONE);
		}
		if (doctorMiniProfilesWithLocation.size() > 0 && selectedDoctorMiniProfile == null)
		{
			selectedDoctorMiniProfile = doctorMiniProfilesWithLocation.get(0);
			/**
			 * First doctor will be selected doctor.
			 * We should remove it from doctorMiniProfilesWithLocationForMap list (for cluster manager), but keep in doctorMiniProfilesWithLocation list (for viewpager)
			 */
			doctorMiniProfilesWithLocationForMap.clear();
			doctorMiniProfilesWithLocationForMap.addAll(doctorMiniProfilesWithLocation.subList(1, doctorMiniProfilesWithLocation.size()));
			//Log.d(getLogTag(), "select profile: " + selectedDoctorMiniProfile);
			fillClusterManagers();
		}
	}
	
	private List<ProfileCardDTO> getDoctorMiniProfilesWithLocation()
	{
		return doctorMiniProfilesWithLocation;
	}
	
	/**
	 * Remove selection from previously selected doctor and select given profile
	 * With small delay bring selected marker to top
	 *
	 * @param profile profile to select
	 */
	@SuppressWarnings("unchecked")
	private void highlightMarker(ProfileCardDTO profile)
	{
		if (selectedDoctorMiniProfile != null && profile.getMapKey().equals(selectedDoctorMiniProfile.getMapKey()))
			return;
		if (inProcessOfClick.size() > 0)
		{
			ProfileCardDTO tmpProfile = new ProfileCardDTO();
			if (selectedDoctorMiniProfile != null && clickedMrk != null)
			{
				if (oldClickedMrk != null && oldClickedMrk.getPosition() != selectedDoctorMiniProfile.getPosition())
				{
					oldClickedMrk.setAlpha(1f);
				}
				clickedMrk.setAlpha(0f);
				clusterManagerForSelected.clearItems();
				tmpProfile.setDifferentProfile(profile);
				tmpProfile.getPractice().setLatitude(String.valueOf(clickedMrk.getPosition()
						.latitude));
				tmpProfile.getPractice().setLongitude(String.valueOf(clickedMrk.getPosition()
						.longitude));
				clusterManagerForSelected.addItem(tmpProfile);
				clusterManagerForSelected.cluster();
				if (inUpdate == false)
				{
					new Handler().postDelayed(() ->
					{
						inUpdate = false;
						if (doctorMarkerRenderer.getMarker(tmpCluster) != null)
							doctorMarkerRenderer.getMarker(tmpCluster).setAlpha(0.5f);
					}, 250);
					inUpdate = true;
				}
			}
			//new Handler().postDelayed(() -> bringMarkerToTop(profile), 100);
		} else
		{
			if (selectedDoctorMiniProfile != null)
			{
				clusterManagerForSelected.clearItems();
				clusterManagerForSelected.addItem(profile);
				clusterManagerForSelected.cluster();
				clusterManager.addItem(selectedDoctorMiniProfile);
				clusterManager.removeItem(profile);
				clusterManager.cluster();
			}
			selectedDoctorMiniProfile = profile;
			new Handler().postDelayed(() -> bringMarkerToTop(profile), 100);
		}
	}
	
	private void initViewPager()
	{
		//Log.d(getLogTag(), "initViewPager");
		doctorCardsPagerAdapter = new DoctorCardsPagerAdapter(getChildFragmentManager(), getActivity(), getDoctorMiniProfilesWithLocation());
		viewPagerDoctorCards.setAdapter(doctorCardsPagerAdapter);
		viewPagerDoctorCards.setOnPageChangeListener(new ViewPager.OnPageChangeListener()
		{
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
			{
			}
			
			@Override
			public void onPageSelected(int position)
			{
				//Log.d(getLogTag(), "onPageSelected: " + position);
				if (position < getDoctorMiniProfilesWithLocation().size())
				{
					ProfileCardDTO doctorMiniProfile = getDoctorMiniProfilesWithLocation().get(position);
					//Log.d(getLogTag(), "doctorMiniProfile found: " + doctorMiniProfile);
					if (selectedDoctorMiniProfile == null || !selectedDoctorMiniProfile.getMapKey().equals(doctorMiniProfile.getMapKey()))
					{
						GoogleMap googleMap = getGoogleMap();
						LatLng latLng = doctorMiniProfile.getPosition();
						/**
						 * we should navigate map to selected doctor
						 */
						Marker marker = doctorMarkerRenderer.getMarker(doctorMiniProfile);
						if (marker != null)
						{
							/**
							 * Marker is visible - animate camera to it position
							 */
							if (inProcessOfClick.size() < 1)
								googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng),
										ANIMATE_DURATION_MS, cancelableCallback);
							highlightMarker(doctorMiniProfile);
						} else
						{
							//Log.d(getLogTag(), "is profile hidden in cluster: yes");
							/**
							 * Marker was hidden in cluster, just animate camera to marker with zoom level 13 and highlight marker after animation is done
							 */
							googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17), ANIMATE_DURATION_MS, new GoogleMap.CancelableCallback()
							{
								@Override
								public void onFinish()
								{
									highlightMarker(doctorMiniProfile);
								}
								
								@Override
								public void onCancel()
								{
									highlightMarker(doctorMiniProfile);
								}
							});
						}
					}
				}
			}
			
			@Override
			public void onPageScrollStateChanged(int state)
			{
			}
		});
	}
	
	GoogleMap.CancelableCallback cancelableCallback = new GoogleMap.CancelableCallback()
	{
		@Override
		public void onFinish()
		{
			//Log.d(getLogTag(), "cancelableCallback onFinish");
			if (clusterManager != null)
			{
				clusterManager.cluster();
			}
		}
		
		@Override
		public void onCancel()
		{
			if (clusterManager != null)
			{
				clusterManager.cluster();
			}
		}
	};
	
	@Override
	public void onStop()
	{
		super.onStop();
//		if (mGoogleApiClient.isConnected())
//			mGoogleApiClient.disconnect();
		getSingleActivity().setNavigationBarVisible(true);
	}//// TODO: 11.07.2016  
	
	@Override
	public void onActivated()
	{
		super.onActivated();
		getSingleActivity().setNavigationBarVisible(false);
		setHomeIconAsX(R.drawable.close_blue);
		getSingleActivity().setLocationLabelVisible(true);
		getSingleActivity().getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}
	
	private void setupMap()
	{
		//Log.d(getLogTag(), "setupMap");
		GoogleMap googleMap = getGoogleMap();
		if (googleMap == null) return;
		/**
		 * Apply top and bottom paddings, so markers will be visible
		 */
		googleMap.setPadding(0, getResources().getDimensionPixelSize(R.dimen.connections_map_top_padding),
				0, getResources().getDimensionPixelSize(R.dimen
						.connections_map_doctor_card_height));
		View zoomControls = getSupportMapFragment().getView().findViewById(0x1);
		RelativeLayout.LayoutParams tmpParams = (RelativeLayout.LayoutParams) zoomControls.getLayoutParams();
		tmpParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		tmpParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
		tmpParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
		tmpParams.addRule(RelativeLayout.ALIGN_PARENT_END, 0);
		tmpParams.setMargins(tmpParams.rightMargin, 0, 0, 0);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
		{
			tmpParams.setMarginStart(tmpParams.leftMargin);
			tmpParams.setMarginEnd(0);
		}
		zoomControls.setLayoutParams(tmpParams);
		for (int i = 0; i < ((ViewGroup) zoomControls).getChildCount(); i++)
		{
			ImageView child = (ImageView) ((ViewGroup) zoomControls).getChildAt(i);
			if (i == 0)
			{
				child.setImageResource(R.drawable.icon_plus);
				LinearLayout.LayoutParams tmpParamsImg = (LinearLayout.LayoutParams) child
						.getLayoutParams();
				tmpParamsImg.setMargins(10, 0, 0, 0);
				child.setLayoutParams(tmpParamsImg);
			}
			if (i == 1)
			{
				child.setImageResource(R.drawable.icon_minus);
				LinearLayout.LayoutParams tmpParamsImg = (LinearLayout.LayoutParams) child
						.getLayoutParams();
				tmpParamsImg.setMargins(0, 30, 0, 0);
				child.setLayoutParams(tmpParamsImg);
			}
		}
		ImageView myLocation = (ImageView) getSupportMapFragment().getView().findViewById(0x2);
		myLocation.setImageResource(R.drawable.icon_mylocation);
		googleMap.setInfoWindowAdapter(new CustomWindowAdapter(getActivity()));
		setupMapMyLocation();
		googleMap.setOnMyLocationChangeListener(location -> eventsBus.post(new MyLocationChangedEvent(location)));
		initializeClusterManager(googleMap);
		googleMap.setOnMarkerClickListener(this);
		googleMap.setOnCameraChangeListener(this);
		googleMap.setOnMapClickListener(latLng -> {
			if (inProcessOfClick.size() > 0)
			{
				clearMarkers();
			}
		});
		addDoctorsToMap();
		initViewPager();
	}
	
	private void setupMapMyLocation()
	{
		if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
		{
			if (getGoogleMap() != null)
			{
				getGoogleMap().getUiSettings().setMyLocationButtonEnabled(true);
				getGoogleMap().setMyLocationEnabled(true);
				getGoogleMap().getUiSettings().setCompassEnabled(false);
			}
		}
	}
	
	public void onEvent(DoctorSubscribedEvent event)
	{
		if (isActiveFragment())
		{
			if ((getDoctorMiniProfilesWithLocation().size() > 0) && (doctorCardsPagerAdapter != null))
			{
				int i1 = -1;
				for (int i = 0; i < getDoctorMiniProfilesWithLocation().size(); i++)
				{
					if (getDoctorMiniProfilesWithLocation().get(i).getUsername().equals(event.getUserName()))
					{
						i1 = i;
						break;
					}
				}
				if (i1 > -1)
				{
					if (event.isFollow() == true)
					{
						getDoctorMiniProfilesWithLocation().get(i1).setFollowStatus(FollowEnum.FOLLOWED);
					} else
					{
						getDoctorMiniProfilesWithLocation().get(i1).setFollowStatus(FollowEnum.NOT_FOLLOWED);
					}
					doctorCardsPagerAdapter.notifyDataSetChanged();
				}
			}
		}
	}
	
	private void resolveSubscribe(ProfileCardDTO doctor)
	{
		if (!isUserAuthenticated())
		{
			inProcessOfSubscribing.remove(doctor);
			addFragment(LoginFragment2.newInstance(false, ""));
			return;
		}
		if (doctor.getFollowStatus() == FollowEnum.FOLLOWED)
		{
			socialAPI.unfollowDoctor(doctor.getUsername(), "")
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(response -> {
						if (response.isSuccessful())
						{
							doctor.setFollowStatus(FollowEnum.NOT_FOLLOWED);
							eventsBus.post(new DoctorSubscribedEvent(doctor.getUsername(), false));
							inProcessOfSubscribing.remove(doctor);
						}
					}, throwable -> {
						Toast.makeText(getContext(), getContext().getString(R.string.error), Toast
								.LENGTH_SHORT)
								.show();
						inProcessOfSubscribing.remove(doctor);
						//Log.e("error", Log.getStackTraceString(throwable));
					});
		} else
		{
			socialAPI.followDoctor(doctor.getUsername(), "")
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(response -> {
						if (response.isSuccessful())
						{
							doctor.setFollowStatus(FollowEnum.FOLLOWED);
							eventsBus.post(new DoctorSubscribedEvent(doctor.getUsername(), true));
						}
						inProcessOfSubscribing.remove(doctor);
					}, throwable -> {
						Toast.makeText(getContext(), getContext().getString(R.string.error), Toast.LENGTH_SHORT).show();
						inProcessOfSubscribing.remove(doctor);
						//Log.e("error", Log.getStackTraceString(throwable));
					});
		}
	}
	
	@Override
	public void onClick(View v, int position)
	{
		//Log.d(getLogTag(), "onClick:" + v);
		ProfileCardDTO doctor = getDoctorMiniProfilesWithLocation().get(position);
		switch (v.getId())
		{
			case R.id.subscribe_icon:
				if (!inProcessOfSubscribing.contains(doctor))
				{
					inProcessOfSubscribing.add(doctor);
					resolveSubscribe(doctor);
				}
				break;
			case R.id.book_button:
				addFragment(DoctorProfileFragment2.newInstance(doctor, 2));
				break;
			case R.id.slot1:
				addFragment(BookingFragment1.newInstance(doctor.getAvailableSlotList().get(0)
						.getStart(), doctor.getAvailableSlotList().get(0), doctor, null, false, false), R.anim.slide_in_right, R.anim.slide_out_right);
				break;
			case R.id.slot2:
				addFragment(BookingFragment1.newInstance(doctor.getAvailableSlotList().get(1)
						.getStart(), doctor.getAvailableSlotList().get(1), doctor, null, false, false), R.anim.slide_in_right, R.anim.slide_out_right);
				break;
			case R.id.slot3:
				addFragment(BookingFragment1.newInstance(doctor.getAvailableSlotList().get(2)
						.getStart(), doctor.getAvailableSlotList().get(2), doctor, null, false, false), R.anim.slide_in_right, R.anim.slide_out_right);
				break;
			case R.id.address_layout:
				addFragment(DoctorProfileFragment2.newInstance(doctor, 1));
				break;
			default:
				addFragment(DoctorProfileFragment2.newInstance(doctor));
				break;
		}
	}
	
	@Override
	public void onTouch(View v, MotionEvent event, GroupDTO group)
	{
	}
	
	private void clearMarkers()
	{
		changeRenderMarkerType(doctorMarkerRenderer.getMarkerType());
		clusterManagerForSelected.clearItems();
		clusterManagerForSelected.cluster();
	}
	
	@Override
	public void onCameraChange(CameraPosition cameraPosition)
	{
		zoomLevel = cameraPosition.zoom;
		if (clusterManager != null)
		{
			clusterManager.onCameraChange(cameraPosition);
			bringMarkerToTop(selectedDoctorMiniProfile);
			if (inProcessOfClick.size() > 0)
			{
				if (zoomLevel != zoomOld)
				{
					clearMarkers();
				} else
				{
					if (inUpdate == false)
					{
						new Handler().postDelayed(() ->
						{
							inUpdate = false;
							if (doctorMarkerRenderer.getMarker(tmpCluster) != null)
								doctorMarkerRenderer.getMarker(tmpCluster).setAlpha(0.5f);
						}, 250);
						inUpdate = true;
					}
				}
			}
		}
		zoomOld = zoomLevel;
	}
	
	@Override
	public boolean onClusterClick(Cluster<ProfileCardDTO> cluster)
	{
		if (inProcessOfClick.size() > 0)
		{
			changeRenderMarkerType(doctorMarkerRenderer.getMarkerType());
			for (Polyline line : polylines)
			{
				line.remove();
			}
			polylines.clear();
		}
		boolean same = true;
		ProfileCardDTO tmpProfile = null;
		for (ProfileCardDTO profile : cluster.getItems())
		{
			if (tmpProfile != null)
			{
				if ((Math.abs(Float.parseFloat(tmpProfile.getPractice().getLatitude()) - Float
						.parseFloat(profile
								.getPractice()
								.getLatitude())) > 0.00001f) || (Math.abs(Float.parseFloat(tmpProfile
						.getPractice().getLongitude()) - Float.parseFloat(profile
						.getPractice()
						.getLongitude())) > 0.00001f))
				{
					same = false;
					break;
				}
			}
			tmpProfile = profile;
		}
		if (zoomLevel >= 12 && same)
		{
			inProcessOfClick.clear();
			int count = cluster.getSize();
			double a = 360.0 / count;
			double radius = 0.0006;
			if (zoomLevel < 17.7)
			{
				radius = 0.0005;
			} else if (zoomLevel < 18.7)
			{
				radius = 0.0003;
			} else if (zoomLevel < 19.7)
			{
				radius = 0.00015;
			} else if (zoomLevel <= 20.7)
			{
				radius = 0.00007;
			} else if (zoomLevel > 20.7)
			{
				radius = 0.00005;
			}
			int i = 0;
			final long duration = 500;
			final long start = SystemClock.uptimeMillis();
			final Interpolator interpolator = new LinearInterpolator();
			for (ProfileCardDTO profile : cluster.getItems())
			{
				MarkerOptions mrk = new MarkerOptions();
				double x = radius * Math.cos((a * i) / 180 * Math.PI);
				double y = radius * Math.sin((a * i) / 180 * Math.PI);
				LatLng latLngEnd = new LatLng(profile.getPosition().latitude + x, profile
						.getPosition().longitude + y);
				LatLng latLngStart = profile.getPosition();
				mrk.position(latLngStart);
				doctorMarkerRenderer.onBeforeClusterItemRendered(profile, mrk);
				Marker tmpMrk = clusterManager.getMarkerCollection().addMarker(mrk);
				Handler handler = new Handler();
				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						long elapsed = SystemClock.uptimeMillis() - start;
						if (elapsed > duration)
							elapsed = duration;
						float t = interpolator.getInterpolation((float) elapsed / duration);
						double lng = t * latLngEnd.longitude + (1 - t) * latLngStart.longitude;
						double lat = t * latLngEnd.latitude + (1 - t) * latLngStart.latitude;
						tmpMrk.setPosition(new LatLng(lat, lng));
						if (t < 1.0)
						{
							handler.postDelayed(this, 10);
						} else
						{
							PolylineOptions line =
									new PolylineOptions().add(cluster.getPosition(),
											cluster.getPosition(),
											latLngEnd,
											latLngEnd)
											.width(5).color(Color.BLACK);
							polylines.add(getGoogleMap().addPolyline(line));
						}
					}
				});
				doctorMarkerRenderer.getmMarkerCache().put(profile, tmpMrk);
				clusterManager.addItem(profile);
				inProcessOfClick.add(profile);
				i++;
			}
			tmpCluster = cluster;
			bringMarkerToTop(selectedDoctorMiniProfile);
			new Handler().postDelayed(() ->
			{
				if (doctorMarkerRenderer.getMarker(cluster) != null)
					doctorMarkerRenderer.getMarker(cluster).setAlpha(0.5f);
			}, 250);
		} else
		{
			LatLngBounds.Builder builder = new LatLngBounds.Builder();
			for (ProfileCardDTO profile : cluster.getItems())
			{
				Practice2 location = profile.getLocation();
				LatLng latLng = new LatLng(Double.parseDouble(location.getLatitude()), Double.parseDouble(location
						.getLongitude()));
				builder.include(latLng);
			}
			LatLngBounds latLngBounds = builder.build();
			CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(latLngBounds, getResources().getDimensionPixelSize(R.dimen.map_padding));
			getGoogleMap().animateCamera(cameraUpdate, ANIMATE_DURATION_MS, new GoogleMap.CancelableCallback()
			{
				@Override
				public void onFinish()
				{
					changeRenderMarkerType(doctorMarkerRenderer.getMarkerType());
				}
				
				@Override
				public void onCancel()
				{
				}
			});
		}
		return true;
	}
	
	private int getCardIndex(ProfileCardDTO doctorMiniProfile)
	{
		int index = 0;
		for (ProfileCardDTO profile : getDoctorMiniProfilesWithLocation())
		{
			if (doctorMiniProfile.getMapKey().equals(profile.getMapKey()))
			{
				return index;
			}
			index++;
		}
		return -1;
	}
	
	@Override
	public boolean onClusterItemClick(ProfileCardDTO profile)
	{
		if (profile == null) return false;
		int index = getCardIndex(profile);
		if (index == -1)
		{
			Toast.makeText(getActivity(), R.string.cant_find_index, Toast.LENGTH_SHORT).show();
			return true;
		}
		if (oldIndex == index)
		{
			highlightMarker(profile);
		} else
		{
			viewPagerDoctorCards.setCurrentItem(index, false);
		}
		oldIndex = index;
		return true;
	}
	
	@Override
	public void onMapReady(GoogleMap googleMap)
	{
		this.map = map;
		setupMap();
//        map.setMyLocationEnabled(true);
	}
	
	@Override
	public boolean onMarkerClick(Marker marker)
	{
		if (!clusterManager.getClusterMarkerCollection().getMarkers().contains(marker))
		{
			oldClickedMrk = clickedMrk;
			clickedMrk = marker;
		}
		clusterManager.onMarkerClick(marker);
		clusterManagerForSelected.onMarkerClick(marker);
		return true;
	}
	
	@Override
	protected String getTitle()
	{
		return "";
	}
}

