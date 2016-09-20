package me.drnear.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;
import java.util.HashMap;

import me.drnear.R;
import me.drnear.rest.model.user.ProfileCardDTO;
import me.drnear.utils.AvatarUtils;

/**
 * Custom renderer
 * <p>
 * Can draw doctor markers in 3 different ways:
 * - time
 * - avatar
 * - specialty
 */
public class DoctorMarkerRenderer extends DefaultClusterRenderer<ProfileCardDTO>
{
	public static final String TAG = DoctorMarkerRenderer.class.getSimpleName();
	private CustomIconGenerator iconGenerator;
	private ImageView mainIcon;
	private ImageView specialtyIcon;
	private ImageView avatarIcon;
	private TextView timeView;
	private Context context;
	private Picasso picasso;
	private ClusterManager<ProfileCardDTO> clusterManager;
	private MarkerType markerType = MarkerType.TIME;
	private boolean forSelectedMarker;
	
	/**
	 * Constructor
	 *
	 * @param context           android context
	 * @param map               google map
	 * @param clusterManager    cluster manager used
	 * @param picasso           picasso instance to load avatars
	 * @param forSelectedMarker TRUE if this renderer is used to render selected marker (big size)
	 * @param markerType        how marker should be rendered
	 */
	public DoctorMarkerRenderer(Context context, GoogleMap map, ClusterManager<ProfileCardDTO> clusterManager, Picasso picasso, boolean forSelectedMarker, MarkerType markerType)
	{
		super(context, map, clusterManager);
		this.context = context;
		this.picasso = picasso;
		this.clusterManager = clusterManager;
		this.forSelectedMarker = forSelectedMarker;
		this.markerType = markerType;
		iconGenerator = new CustomIconGenerator(context);
		View pinLayout = LayoutInflater.from(context).inflate(R.layout.map_doctor_pin_layout, null);
		iconGenerator.setContentView(pinLayout);
		iconGenerator.setColor(android.R.color.transparent);
		mainIcon = (ImageView) pinLayout.findViewById(R.id.icon);
		specialtyIcon = (ImageView) pinLayout.findViewById(R.id.specialty_icon);
		avatarIcon = (ImageView) pinLayout.findViewById(R.id.avatar);
		timeView = (TextView) pinLayout.findViewById(R.id.text);
	}
	
	@Override
	protected void onBeforeClusterItemRendered(final ProfileCardDTO item, MarkerOptions markerOptions)
	{
		super.onBeforeClusterItemRendered(item, markerOptions);
		//Log.d(getLogTag(), "onBeforeClusterItemRendered: " + item + ", options: " + 
		//	markerOptions + ", markerType: " + markerType);
		// green marker for selected doctor, blue otherwise
		mainIcon.setImageResource(forSelectedMarker ? R.drawable.markerselected : R.drawable.markerblue);
		String availableSlotTime = item.getAvailableSlotTime();
		String timeText = availableSlotTime != null ? availableSlotTime : "N/A";
		timeView.setText(timeText);
		timeView.setVisibility(markerType == MarkerType.TIME ? View.VISIBLE : View.GONE);
		picasso.load(item.getSpecialtyThumbnailUrl()).error(R.drawable.dent)
				.into(specialtyIcon);
		specialtyIcon.setVisibility(markerType == MarkerType.SPECIALTY ? View.VISIBLE : View.GONE);
		avatarIcon.setVisibility(markerType == MarkerType.AVATAR ? View.VISIBLE : View.GONE);
		/**
		 * If we have doctor avatar in cache - get it and use it
		 * Or request it if we don't have cached bitmap
		 */
		Bitmap avatar = getAvatarBitmapFromCache(item);
		if (avatar != null)
		{
			avatarIcon.setImageBitmap(avatar);
		} else
		{
			requestAvatar(item);
		}
		Bitmap bitmap = iconGenerator.makeIcon();
		if (!forSelectedMarker)
		{
			/**
			 * By default marker side is 80dp (defined in xml layout). For selected marker we keep this value.
			 * For not selected marker we resize marker, so it will be smaller (70dp)
			 *
			 * Because we will resize to smaller size, both markers will look good
			 */
			int size = context.getResources().getDimensionPixelSize(R.dimen.connection_map_pin_size);
			bitmap = Bitmap.createScaledBitmap(bitmap, size, size, false);
		}
		markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
	}
	
	public MarkerType getMarkerType()
	{
		return markerType;
	}
	
	/**
	 * Request avatar image for doctor.
	 * Then image loaded - update doctor marker
	 */
	private void requestAvatar(ProfileCardDTO doctorMiniProfile)
	{
		picasso.load(doctorMiniProfile.getThubnailUrl())
				.into(new Target()
				{
					@Override
					public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from)
					{
						Log.d(getLogTag(), "onBitmapLoaded: " + from);
						DoctorMarkerRenderer.this.updateMarker(doctorMiniProfile);
					}
					
					@Override
					public void onBitmapFailed(Drawable errorDrawable)
					{
						Log.d(getLogTag(), "onBitmapFailed");
					}
					
					@Override
					public void onPrepareLoad(Drawable placeHolderDrawable)
					{
						Log.d(getLogTag(), "onPrepareLoad");
					}
				});
	}
	
	public Bitmap getAvatarBitmapFromCache(ProfileCardDTO doctorMiniProfile)
	{
		CacheTarget cacheTarget = new CacheTarget();
		picasso.load(doctorMiniProfile.getThubnailUrl()).transform(new CircularTransformation()).into(cacheTarget);
		return cacheTarget.getCacheBitmap();
	}
	
	@Override
	protected void onBeforeClusterRendered(Cluster<ProfileCardDTO> cluster, MarkerOptions markerOptions)
	{
		//Log.d(getLogTag(), "onBeforeClusterRendered: " + cluster + ", options: " + markerOptions);
		int bucket = getBucket(cluster);
		mColoredCircleBackground.getPaint().setColor(getBucketColor(cluster));
		BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(mIconGenerator.makeIcon(getClusterText(bucket)));
		markerOptions.icon(descriptor);
	}
	
	private int getBucketColor(Cluster<ProfileCardDTO> cluster)
	{
		int bucket = getBucket(cluster);
		return getColor(bucket);
	}
	
	/**
	 * Could be customized. For now leave it as is
	 */
	@Override
	protected boolean shouldRenderAsCluster(Cluster<ProfileCardDTO> cluster)
	{
		boolean result = super.shouldRenderAsCluster(cluster);
		//Log.d(getLogTag(), "shouldRenderAsCluster: " + result);
		return result;
	}
	
	private String getLogTag()
	{
		return TAG + "#" + (forSelectedMarker ? "1" : "0");
	}
}
