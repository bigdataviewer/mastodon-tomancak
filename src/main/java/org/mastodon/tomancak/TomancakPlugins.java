package org.mastodon.tomancak;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.UIManager;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.project.MamutProject;
import org.mastodon.project.MamutProjectIO;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.Mastodon;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.ui.keymap.CommandDescriptionProvider;
import org.mastodon.revised.ui.keymap.CommandDescriptions;
import org.scijava.AbstractContextual;
import org.scijava.Context;
import org.scijava.plugin.Plugin;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

@Plugin( type = TomancakPlugins.class )
public class TomancakPlugins extends AbstractContextual implements MastodonPlugin
{
	private static final String EXPORT_PHYLOXML = "[tomancak] export phyloxml for selection";
	private static final String FLIP_DESCENDANTS = "[tomancak] flip descendants";
	private static final String COPY_TAG = "[tomancak] copy tag";
	private static final String INTERPOLATE_SPOTS = "[tomancak] interpolate spots";
	private static final String IMPORT_FROM_IMAGES = "[tomancak] import from instance segmentation";

	private static final String POINTS_EXPORT_3COLS = "[tomancak] export spots as 3col points";
	private static final String POINTS_IMPORT_3COLS = "[tomancak] import spots from 3col points";
	private static final String POINTS_EXPORT_4COLS = "[tomancak] export spots as 4col points";
	private static final String POINTS_IMPORT_4COLS = "[tomancak] import spots from 4col points";

	private static final String[] EXPORT_PHYLOXML_KEYS = { "not mapped" };
	private static final String[] FLIP_DESCENDANTS_KEYS = { "not mapped" };
	private static final String[] COPY_TAG_KEYS = { "not mapped" };
	private static final String[] INTERPOLATE_SPOTS_KEYS = { "not mapped" };
	private static final String[] IMPORT_FROM_IMAGES_KEYS = { "not mapped" };

	private static final String[] POINTS_EXPORT_3COLS_KEYS = { "not mapped" };
	private static final String[] POINTS_IMPORT_3COLS_KEYS = { "not mapped" };
	private static final String[] POINTS_EXPORT_4COLS_KEYS = { "not mapped" };
	private static final String[] POINTS_IMPORT_4COLS_KEYS = { "not mapped" };

	private static Map< String, String > menuTexts = new HashMap<>();

	static
	{
		menuTexts.put( EXPORT_PHYLOXML, "Export phyloXML for selection" );
		menuTexts.put( FLIP_DESCENDANTS, "Flip descendants" );
		menuTexts.put( COPY_TAG, "Copy Tag..." );
		menuTexts.put( INTERPOLATE_SPOTS, "Interpolate Missing Spots" );
		menuTexts.put( IMPORT_FROM_IMAGES, "Import from instance segmentation" );

		menuTexts.put( POINTS_EXPORT_3COLS, "Export to 3-column files" );
		menuTexts.put( POINTS_IMPORT_3COLS, "Import from 3-column file" );
		menuTexts.put( POINTS_EXPORT_4COLS, "Export to 4-column file" );
		menuTexts.put( POINTS_IMPORT_4COLS, "Import from 4-column file" );
	}

	/*
	 * Command descriptions for all provided commands
	 */
	@Plugin( type = Descriptions.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super( KeyConfigContexts.TRACKSCHEME, KeyConfigContexts.BIGDATAVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			descriptions.add( EXPORT_PHYLOXML, EXPORT_PHYLOXML_KEYS, "Export subtree to PhyloXML format." );
			descriptions.add( FLIP_DESCENDANTS, FLIP_DESCENDANTS_KEYS, "Flip children in trackscheme graph." );
			descriptions.add( COPY_TAG, COPY_TAG_KEYS, "Copy tags: everything that has tag A assigned gets B assigned." );
			descriptions.add( INTERPOLATE_SPOTS, INTERPOLATE_SPOTS_KEYS, "Interpolate missing spots." );
		}
	}

	private final AbstractNamedAction exportPhyloXmlAction;

	private final AbstractNamedAction flipDescendantsAction;

	private final AbstractNamedAction copyTagAction;

	private final AbstractNamedAction interpolateSpotsAction;

	private final AbstractNamedAction importFromImagesAction;

	private final AbstractNamedAction exportThreeColumnPointsPerTimepointsAction;
	private final AbstractNamedAction importThreeColumnPointsAction;
	private final AbstractNamedAction exportFourColumnPointsAction;
	private final AbstractNamedAction importFourColumnPointsAction;

	private MastodonPluginAppModel pluginAppModel;

	public TomancakPlugins()
	{
		exportPhyloXmlAction = new RunnableAction( EXPORT_PHYLOXML, this::exportPhyloXml );
		flipDescendantsAction = new RunnableAction( FLIP_DESCENDANTS, this::flipDescendants );
		copyTagAction = new RunnableAction( COPY_TAG, this::copyTag );
		interpolateSpotsAction = new RunnableAction( INTERPOLATE_SPOTS, this::interpolateSpots );
		importFromImagesAction = new RunnableAction( IMPORT_FROM_IMAGES, this::importFromImages );

		exportThreeColumnPointsPerTimepointsAction = new RunnableAction( POINTS_EXPORT_3COLS, this::exportThreeColumnPointsPerTimepoints );
		importThreeColumnPointsAction              = new RunnableAction( POINTS_IMPORT_3COLS, this::importThreeColumnPoints );
		exportFourColumnPointsAction               = new RunnableAction( POINTS_EXPORT_4COLS, this::exportFourColumnPoints );
		importFourColumnPointsAction               = new RunnableAction( POINTS_IMPORT_4COLS, this::importFourColumnPoints );


		updateEnabledActions();
	}

	@Override
	public void setAppModel( final MastodonPluginAppModel model )
	{
		this.pluginAppModel = model;
		updateEnabledActions();
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Arrays.asList(
				menu( "Plugins",
						menu( "Tomancak lab",
								menu( "Spots from/to TXT files",
									item( POINTS_EXPORT_3COLS ),
									item( POINTS_IMPORT_3COLS ),
									item( POINTS_EXPORT_4COLS ),
									item( POINTS_IMPORT_4COLS ) ),
								item( EXPORT_PHYLOXML ),
								item( FLIP_DESCENDANTS ),
								item( INTERPOLATE_SPOTS ),
								item( COPY_TAG ),
								item( IMPORT_FROM_IMAGES ) ) ) );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}

	@Override
	public void installGlobalActions( final Actions actions )
	{
		actions.namedAction( exportPhyloXmlAction, EXPORT_PHYLOXML_KEYS );
		actions.namedAction( flipDescendantsAction, FLIP_DESCENDANTS_KEYS );
		actions.namedAction( copyTagAction, COPY_TAG_KEYS );
		actions.namedAction( interpolateSpotsAction, INTERPOLATE_SPOTS_KEYS );
		actions.namedAction( importFromImagesAction, IMPORT_FROM_IMAGES_KEYS );

		actions.namedAction( exportThreeColumnPointsPerTimepointsAction, POINTS_EXPORT_3COLS_KEYS );
		actions.namedAction( importThreeColumnPointsAction,              POINTS_IMPORT_3COLS_KEYS );
		actions.namedAction( exportFourColumnPointsAction,               POINTS_EXPORT_4COLS_KEYS );
		actions.namedAction( importFourColumnPointsAction,               POINTS_IMPORT_4COLS_KEYS );
	}

	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		exportPhyloXmlAction.setEnabled( appModel != null );
		flipDescendantsAction.setEnabled( appModel != null );
		importFromImagesAction.setEnabled( appModel != null );

		exportThreeColumnPointsPerTimepointsAction.setEnabled( appModel != null );
		importThreeColumnPointsAction.setEnabled( appModel != null );
		exportFourColumnPointsAction.setEnabled( appModel != null );
		importFourColumnPointsAction.setEnabled( appModel != null );
	}

	private void exportPhyloXml()
	{
		if ( pluginAppModel != null )
			MakePhyloXml.exportSelectedSubtreeToPhyloXmlFile( pluginAppModel.getAppModel() );
	}

	private void flipDescendants()
	{
		if ( pluginAppModel != null )
			FlipDescendants.flipDescendants( pluginAppModel.getAppModel() );
	}

	private void copyTag()
	{
		if ( pluginAppModel != null )
		{
			final Model model = pluginAppModel.getAppModel().getModel();
			new CopyTagDialog( null, model ).setVisible( true );
		}
	}

	private void interpolateSpots()
	{
		if ( pluginAppModel != null )
		{
			final Model model = pluginAppModel.getAppModel().getModel();
			InterpolateMissingSpots.interpolate( model );
		}
	}

	private void importFromImages()
	{
		if ( pluginAppModel == null ) return;

		this.getContext().getService(CommandService.class).run(
			ReadInstanceSegmentationImages.class, true,
			"appModel", pluginAppModel.getAppModel(),
			"logService", this.getContext().getService(LogService.class));
	}

	private void exportThreeColumnPointsPerTimepoints()
	{
		if ( pluginAppModel != null )
			WritePointsTXT.exportThreeColumnPointsPerTimepoints( pluginAppModel.getAppModel() );
	}
	private void importThreeColumnPoints()
	{
		if ( pluginAppModel != null )
			ReadPointsTXT.importThreeColumnPoints( pluginAppModel.getAppModel() );
	}

	private void exportFourColumnPoints()
	{
		if ( pluginAppModel != null )
			WritePointsTXT.exportFourColumnPoints( pluginAppModel.getAppModel() );
	}
	private void importFourColumnPoints()
	{
		if ( pluginAppModel != null )
			ReadPointsTXT.importFourColumnPoints( pluginAppModel.getAppModel() );
	}

	/*
	 * Start Mastodon ...
	 */

	public static void main( final String[] args ) throws Exception
	{
		final String projectPath = "/Users/pietzsch/Desktop/Mastodon/merging/Mastodon-files_SimView2_20130315/1.SimView2_20130315_Mastodon_Automat-segm-t0-t300";

		Locale.setDefault( Locale.US );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		final Mastodon mastodon = new Mastodon();
		new Context().inject( mastodon );
		mastodon.run();

		final MamutProject project = new MamutProjectIO().load( projectPath );
		mastodon.openProject( project );
	}
}
