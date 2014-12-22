import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;

public class Loader {

	/**
	 * An simple class for filtering jpg and png files
	 * @author Tobias Kremer
	 */
	private class JPGAndPNGFileFilter implements FileFilter{

		@Override
		public boolean accept(File arg0) {

			if (arg0.getName().endsWith(".jpg")
					|| arg0.getName().endsWith(".png"))
				return true;
			return false;
		}
	}

	private String mainSiteURL = new String("http://alpha.wallhaven.cc");
	private String searchSubString = new String("/search?");
	private String categoriesSubString = new String("categories=111");
	private String puritySubString = new String("purity=100");
	private String sortingSubString = new String("sorting=views");
	private String orderSubString = new String("order=desc");
	private String siteSubString = new String("page=");;
	private String concatSubString = new String("&");

	private File downloadLocation = new File(System.getProperty("user.dir")
			+ File.separator + "Download" + File.separator + "");
	private ArrayList<Integer> idList = new ArrayList<>();

	private ArrayList<String> preferedToken = new ArrayList<>();
	private int threshold;


	
	public Loader() {

		this(new String[] { "abstract", "sunset", "mountains", "landscape",
				"skyscape", "cityscape", "landscapes", "cityscapes", "graph",
				"computer science", "stars", "outer space", "galaxies",
				"beach", "beaches", "water", "nature", "fields", "sunlight",
				"depth of field", "skyscape", "shadow", "clouds", "bokeh", "lenses",
				"wireframes", "wireframe", "science", "minimal", "minimalism", "space",
				"skies", "macro", "closeup"
		});

	}

	/**
	 * Constructor taking an array of strings as tags. These tags are preferred and will
	 * be used to decide whether an image is "prefered" or not.
	 * @param token
	 */
	public Loader(String[] token) {

		this(token, 1);

	}

	/**
	 * Constructor taking an array of strings as tags. These tags are preferred and will
	 * be used to decide whether an image is "prefered" or not. The threshhold is the number
	 * of prefered tags an image has to have to get downloaded.
	 * @param token
	 * @param threshhold
	 */
	public Loader(String[] token, int threshhold) {

		this.threshold = threshhold;

		if (token != null) {

			for (String t : token) {
				this.preferedToken.add(t.toLowerCase());
			}

		}

		if (!this.downloadLocation.exists()) {
			this.downloadLocation.mkdir();
		}

		System.out.println("Starting toplist search... Saving to "
				+ downloadLocation);
		findNewWallpaperOnHotPage();

	}

	/**
	 * Starts parsing the "hotpage" of wallhaven.cc. This is the latest site
	 * sorted after SFW and views of the images.
	 */
	private void findNewWallpaperOnHotPage() {

		String parsingURL = mainSiteURL + searchSubString + categoriesSubString
				+ concatSubString + puritySubString + concatSubString
				+ sortingSubString + concatSubString + orderSubString;


		for (File f : this.downloadLocation.listFiles(new JPGAndPNGFileFilter())) {
			// Add the names to the idList (the names are the ids of the images)
			String fileName = f.getName();
			this.idList.add(Integer.parseInt(fileName.substring(0, fileName.length() - 4)));
		}

		// TODO: Figure out a way to get the maximum index
		for (int index = 0; index < 3000000; ++index) {
			try {

				URL hotPage = new URL(parsingURL + concatSubString + siteSubString
						+ index);
				String[] content = getURLContentAsStringArray(hotPage);

				LinkedList<Integer> currentImageIdsOnPage = getImageIdsFromContent(content);

				for (Integer id : currentImageIdsOnPage) {

					if (!alreadyDownloaded(id)) {
						loadImage(new Image(content, id));
					} else {
						System.out.println("Already owning Image with Id:"
								+ id);
					}
				}

				System.out.println("Current Page:" + index);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}


	/**
	 * Check whether a given id has already been downloaded and return the
	 * the result.
	 * @param id
	 * @return 
	 * boolean
	 */
	private boolean alreadyDownloaded(Integer id) {

		for (int i : this.idList) {
			if (i == id) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Check the given string array for appearance of image ids and
	 * return them as an integer list.
	 * @param content
	 * @return
	 * LinkedList<Integer>
	 */
	private LinkedList<Integer> getImageIdsFromContent(String[] content) {
		
		LinkedList<Integer> ids = new LinkedList<>();

		// Each image is assumed to be 15 lines long
		for (int i = 0; i < content.length; ++i) {
			
			content[i] = content[i].trim();
			
			if(content[i].startsWith("><a class=\"preview\" href=\"")){
				String[] parts = content[i].split("/");
				
				String imageId = parts[parts.length - 1];
				// Trim of the quote
				imageId = imageId.substring(0, imageId.length() - 1);
				
				ids.push(new Integer(imageId));
			}
		}
		
		return ids;
	}

	/**
	 * Return the homepage located at <em>url<em> and save its content as a 
	 * String array.
	 * @param url
	 * @return String[]
	 */
	public static String[] getURLContentAsStringArray(URL url) {
		StringBuffer content = new StringBuffer();

		try {
			
			BufferedReader bf = new BufferedReader(new InputStreamReader(
					url.openStream()));

			while (bf.ready()) {
				content.append(bf.readLine());
				content.append("\n");

				// Handle latency
				if (!bf.ready()) {
					Thread.sleep(5);
				}
			}

			bf.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("Can't open String to URL:" + url.getFile());
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String[] res = content.toString().split("\n");
		
		for(int i = 0; i < res.length; ++i)
			res[i] = res[i].trim();

		return res;
	}

	/**
	 * Check the filePath and the priority and download the image if
	 * the priority is larger or equals the threshold.
	 * @param img
	 * @return void
	 */
	private void loadImage(Image img) {

		int priority = 0;
		int id = 0;

		if (img.filePath != null) {

			// Decide whether a site contains correct token (image description)
			priority = calculatePriority(img.tagList);

			// Load the image, if it contains at least threshold token
			if (priority >= this.threshold) {

				try {
					downloadImage(img);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// Logging information to the current image
				printInformation(priority, img.id, true);
			} else {
				// Logging information to the current image
				printInformation(priority, img.id, true);
			}

		} else {
			// Logging information to the current image
			printInformation(priority, id, false);
		}

	}

	/**
	 * Downloads the image to the download location
	 * @param img
	 * @throws IOException
	 */
	private void downloadImage(Image img) throws IOException {

		URL url = new URL(img.filePath);
		InputStream is = url.openStream();
		OutputStream os = new FileOutputStream(
				this.downloadLocation.getAbsolutePath() + File.separator
						+ img.fileName);

		byte[] b = new byte[200048];
		int length;

		while ((length = is.read(b)) != -1) {
			os.write(b, 0, length);
		}

		is.close();
		os.close();

	}

	private int calculatePriority(ArrayList<String> tagList) {

		int feasibleToken = 0;

		for (String token : tagList) {
			if (this.preferedToken.contains(token)) {
				feasibleToken++;
			}
		}

		return feasibleToken;
	}

	private void printInformation(int priority, long id,
			boolean b) {

			System.out.print("Current Image has ID:" + id 
					+  ", Priority: " + priority);

		System.out
				.println(" "
						+ new Timestamp(Calendar.getInstance()
								.getTimeInMillis()) + " ");

	}

}
