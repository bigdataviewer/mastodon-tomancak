package org.mastodon.tomancak;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JFrame;
import javax.swing.BoxLayout;

import ij.ImagePlus;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.io.File;
import java.util.HashMap;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;

import bdv.viewer.Source;
import net.imglib2.Cursor;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;

import org.mastodon.revised.model.AbstractModelImporter;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;
import org.mastodon.collection.IntRefMap;
import org.mastodon.collection.RefMaps;

@Plugin( type = Command.class, name = "Instance segmentation importer @ Mastodon" )
public class ReadInstanceSegmentationImages
extends ContextCommand
{
	// ----------------- where is the formated result -----------------
	//the image data is in this dataset (unless loaded from external images)
	//@Parameter(label = "Choose (tracks.txt) lineage file that corresponds to the current data:")
	File inputPath;

	//@Parameter(label = "Use external images (maskXXX.tif) from next to the lineage file:")
	boolean useExternalImages = false;

	@Parameter(label = "Link spots that come from the same labels:",
	           description = "This option assumes that labels from the input images are unique for one tracklet.")
	boolean shouldLinkSameLabels = false;

	// ----------------- what is currently displayed in the project -----------------
	@Parameter
	Source<?> imgSource;

	//use always the highest resolution possible
	private final int viewMipLevel = 0;

	// ----------------- where to store the result -----------------
	@Parameter
	Model model;

	@Parameter(label = "Import from this time point:", min="0")
	int timeFrom;

	@Parameter(label = "Import till this time point:", min="0")
	int timeTill;

	@Parameter(label = "Checks if created spots overlap with their markers significantly:")
	boolean doMatchCheck = true;

	public ReadInstanceSegmentationImages()
	{
		//now empty...
	}


	private
	RandomAccessibleInterval<?> fetchImage(final int time)
	{
		if (useExternalImages)
		{
			final String filename = String.format("%s%s%s%03d.tif",
				inputPath.getParentFile().getAbsolutePath(),
				File.separatorChar,"mask",time);

			logServiceRef.info("Reading image: "+filename);
			RandomAccessibleInterval<?> img;
			try
			{
				img = ImageJFunctions.wrap(new ImagePlus( filename ));
			}
			catch (RuntimeException e)
			{
				throw new IllegalArgumentException("Error reading image file "+filename+"\n"+e.getMessage());
			}

			//make sure we always return some non-null reference
			if (img == null)
				throw new IllegalArgumentException("Error reading image file "+filename);
			return img;
		}
		else
			return imgSource.getSource(time,viewMipLevel);
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void run()
	{
		//info or error report
		logServiceRef = this.getContext().getService(LogService.class).log();

		//reset the shortcut variable
		final ModelGraph modelGraph = model.getGraph();

		//debug report
		logServiceRef.info("Time points span is  : "+timeFrom+"-"+timeTill);

		//PROGRESS BAR stuff
		final ButtonHandler pbtnHandler = new ButtonHandler();

		final ProgressIndicator pbar = new ProgressIndicator("Time points processed: ", "", 0, timeTill-timeFrom+1, false);
		final Button pbtn = new Button("Stop importing");
		pbtn.setMaximumSize(new Dimension(150, 40));
		pbtn.addActionListener(pbtnHandler);

		//populate the bar and show it
		final JFrame pbframe = new JFrame("Importer Progress Bar @ Mastodon");
		pbframe.setLayout(new BoxLayout(pbframe.getContentPane(), BoxLayout.Y_AXIS));
		pbframe.add(pbar);
		pbframe.add(pbtn);
		pbframe.setMinimumSize(new Dimension(300, 100));
		pbframe.setLocationByPlatform(true);
		pbframe.setVisible(true);
		//PROGRESS BAR stuff

		new AbstractModelImporter< Model >( model ){{ startImport(); }};

		//transformation used
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();

		//some more dimensionality-based attributes
		inImgDims = imgSource.getSource(timeFrom,viewMipLevel).numDimensions();
		position = new int[inImgDims];

		recentlyUsedSpots = RefMaps.createIntRefMap( modelGraph.vertices(), -1, 500 );
		linkRef = modelGraph.edgeRef();
		nSpot = modelGraph.vertices().createRef();
		oSpot = modelGraph.vertices().createRef();

		try
		{
			//iterate through time points and extract spots
			for (int time = timeFrom; time <= timeTill && isCanceled() == false && !pbtnHandler.buttonPressed(); ++time)
			{
				logServiceRef.info("Processing time point: "+time);

				imgSource.getSourceTransform(time,viewMipLevel, coordTransImg2World);
				readSpots( (IterableInterval)Views.iterable( fetchImage(time) ),
							  time, coordTransImg2World, modelGraph );

				pbar.setProgress(time+1-timeFrom);
			}
		}
		finally
		{
			pbtn.removeActionListener(pbtnHandler);
			pbframe.dispose();

			modelGraph.vertices().releaseRef(oSpot);
			modelGraph.vertices().releaseRef(nSpot);
			modelGraph.releaseRef(linkRef);
		}

		new AbstractModelImporter< Model >( model ){{ finishImport(); }};
		logServiceRef.info("Done.");
	}


	//some shortcut variables worth remembering
	private int inImgDims = -1;
	private int[] position;         //aux px coordinate
	private LogService logServiceRef;
	private IntRefMap< Spot > recentlyUsedSpots;
	private Spot nSpot,oSpot;       //spots references
	private Link linkRef;           //link reference
	final private double[][] cov = new double[3][3];
	final private double[][] T   = new double[3][3];
	final private double[][] Tc  = new double[3][3];

	private <T extends NativeType<T> & RealType<T>>
	void readSpots(final IterableInterval<T> img, final int time,
	               final AffineTransform3D transform,
	               final ModelGraph modelGraph)
	{
		//description of a marker:
		class Marker
		{
			Marker(final T l)
			{
				label = l.copy();
				accCoords = new double[inImgDims];
			}

			//label (voxel value) of this marker
			final T label;

			//volume/area of the marker
			long size;

			//overlap of this marker with its corresponding spot
			long markerOverlap;

			//accumulated coordinates
			double[] accCoords;

			//z-coordinate span
			int minZ=inImgDims < 3 ? 0 : Integer.MAX_VALUE;
			int maxZ=inImgDims < 3 ? 0 : Integer.MIN_VALUE;
		}

		//markers discovered in this image
		HashMap<T,Marker> currentMarkers = new HashMap<>(100);

		//sweep the image and define the markers
		final Cursor<T> voxelCursor = img.localizingCursor();
		while (voxelCursor.hasNext())
		if (voxelCursor.next().getRealFloat() > 0)
		{
			//get functional reference on a marker description
			Marker m = currentMarkers.get(voxelCursor.get());
			if (m == null)
			{
				m = new Marker( voxelCursor.get() );
				currentMarkers.put(m.label, m);
			}

			//update it...
			m.size++;

			voxelCursor.localize(position);
			for (int i=0; i < inImgDims; ++i)
				m.accCoords[i] += position[i];

			if (inImgDims > 2)
			{
				m.minZ = position[2] < m.minZ ? position[2] : m.minZ;
				m.maxZ = position[2] > m.maxZ ? position[2] : m.maxZ;
			}
		}

		//process markers and create respective spots in Mastodon
		for (final Marker m : currentMarkers.values())
		{
			final int label = (int)m.label.getRealFloat();

			//finalize the geometrical centre coordinate (img coords, in px)
			for (int i=0; i < inImgDims; ++i)
				m.accCoords[i] /= m.size;

			//convert the coordinate into Mastodon's world coordinate
			transform.apply(m.accCoords,m.accCoords);

			//estimate radius...
			if (m.minZ == m.maxZ)
			{
				//...as if marker is 2D
				final double r = Math.sqrt( (double)m.size / Math.PI );
				cov[0][0] = r*r;
				cov[1][1] = r*r;
				cov[2][2] = 0.5; //NB: 0.7 * 0.7 = 0.5 -> z thickness is 1.4 px around the marker's centre
			}
			else
			{
				//...as if marker is 3D
				final double r = Math.cbrt( 0.75 * (double)m.size / Math.PI );
				cov[0][0] = r*r;
				cov[1][1] = r*r;
				cov[2][2] = r*r;
			}
			//reset non-diagonal elements
			cov[0][1] = 0; cov[0][2] = 0;
			cov[1][0] = 0; cov[1][2] = 0;
			cov[2][0] = 0; cov[2][1] = 0;

			//adapt the canonical/img-based covariance to Mastodon's world coordinate system
			for ( int r = 0; r < 3; ++r )
				for ( int c = 0; c < 3; ++c )
					T[r][c] = transform.get( r, c );
			LinAlgHelpers.mult( T, cov, Tc );
			LinAlgHelpers.multABT( Tc, T, cov );

			//System.out.println("adding spot at "+Util.printCoordinates(m.accCoords)+" with label="+label);
			nSpot = modelGraph.addVertex( nSpot ).init( time, m.accCoords, cov );

			if (shouldLinkSameLabels && recentlyUsedSpots.containsKey(label))
			{
				//was detected also in the previous frame
				//System.out.println("linking spot with its previous occurrence");

				recentlyUsedSpots.get(label, oSpot);
				modelGraph.addEdge( oSpot, nSpot, linkRef ).init();
			}
			else
			{
				//is detected for the first time: is it after a division?
				//attempt to detect if division occured here
			}

			//in any case, add-or-replace the association of nSpot to this label
			recentlyUsedSpots.put(label, nSpot);

			//NB: we're not removing finished tracks TODO??
			//NB: we shall not remove finished tracks until we're sure they are no longer parents to some future tracks
		}

		//check markers vs. created spots how well do they overlap
		if (doMatchCheck)
		{
			final double[] positionV = new double[inImgDims];
			final double[] positionS = new double[inImgDims];

			//sweep the image and define the markers
			voxelCursor.reset();
			while (voxelCursor.hasNext())
			if (voxelCursor.next().getRealFloat() > 0)
			{
				//Mastodon's world coordinate of this voxel (of this voxel's centre)
				voxelCursor.localize(positionV);
				transform.apply(positionV, positionV);

				//found some marker voxel, find its spot,
				//and increase overlap counter if voxel falls into the spot
				recentlyUsedSpots.get((int)voxelCursor.get().getRealFloat(), nSpot);
				nSpot.localize(positionS);
				nSpot.getCovariance(cov);

				double sum=0;
				for (int i=0; i < inImgDims && i < 3; ++i)
				{
					positionV[i] -= positionS[i];
					positionV[i] *= positionV[i];
					positionV[i] /= cov[i][i];
					sum += positionV[i];
				}

				//is the voxel "covered" with the spot?
				if (sum <= 1.0)
					currentMarkers.get(voxelCursor.get()).markerOverlap++;
			}

			//now scan over the markers and check the matching criterion
			for (final Marker m : currentMarkers.values())
			{
				//System.out.println((int)m.label.getRealFloat()+": "+m.markerOverlap+" / "+m.size);
				if (2*m.markerOverlap < m.size)
					logServiceRef.log(LogLevel.ERROR,
					                  "time "+time
					                  +": spot "+recentlyUsedSpots.get((int)m.label.getRealFloat(),nSpot).getLabel()
					                  +" does not cover image marker "+(int)m.label.getRealFloat());
			}
		}
	}


	///a single-purpose, button-event-handler, aux class
	public class ButtonHandler implements ActionListener
	{
		//whitnessed the event already?
		private boolean buttonPressed = false;

		@Override
		public void actionPerformed(ActionEvent e)
		{ buttonPressed = true; }

		public boolean buttonPressed()
		{ return buttonPressed; }
	}
}
