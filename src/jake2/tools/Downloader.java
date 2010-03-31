package jake2.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Downloader implements Runnable {

	static final String[] MIRRORS = {
		"ftp://ftp.fu-berlin.de/pc/msdos/games/idgames/idstuff/quake2/q2-314-demo-x86.exe",
		"ftp://ftp.cs.tu-berlin.de/pub/msdos/mirrors/ftp.idsoftware.com/quake2/q2-314-demo-x86.exe",
		"ftp://ftp.demon.co.uk/pub/mirrors/idsoftware/quake2/q2-314-demo-x86.exe",
		"ftp://ftp.fragzone.se/pub/spel/quake2/q2-314-demo-x86.exe",
		"ftp://ftp.idsoftware.com/idstuff/quake2/q2-314-demo-x86.exe",
		"ftp://ftp.gamers.org/pub/games/idgames2/idstuff/quake2/q2-314-demo-x86.exe"
	};
	
	public static void main(String[] args) throws MalformedURLException, InterruptedException {
		if (new File("raw", "baseq2").exists()) {
			System.out.println("raw/baseq2 already exists; no need to download");
			return;
		}
		int m0 = selectInitialMirror();
		for (int i = 0; i < MIRRORS.length * 2; i++) {
			Downloader download = new Downloader(MIRRORS[(i + m0) % MIRRORS.length]);
			Thread t = new Thread(download);
			t.start();
			t.join();
			if (download.ok) {
				return;
			}
		}
		throw new RuntimeException("Too many retries. Download failed.");
	}
	
	public static int selectInitialMirror() {
		String domain = Locale.getDefault().getCountry();
		if (domain != null) {
			domain = "." + domain.toLowerCase() + "/";
			if (domain.equals(".us/")) {
				domain = ".com/";
			} else if (domain.equals(".gb/")) {
				domain = ".uk/";
			}
			for (int i = 0; i < MIRRORS.length; i++) {
				if (MIRRORS[i].indexOf(domain) != -1) {
					return i;
				}
			}
		}
		return ((int) (Math.random() * 1000));
	}
	
	private final URL url;
	private byte[] buf = new byte[65536];
	boolean ok;
	
	public Downloader(String url) throws MalformedURLException {
		this.url = new URL(url);
		System.out.println("Trying mirror: " + url);
	}

	void copyStream(InputStream is, OutputStream os, boolean showProgress) throws IOException {
		int total = 0;
		int dots = 0;
		
		while (true) {
			int count = is.read(buf, 0, buf.length);
			if (count <= 0) {
				break;
			}
			total += count;
			if (showProgress) {
				if (total / 1000000 > dots) {
					System.out.print(".");
					dots++;
				}
			}
			os.write(buf, 0, count);
		}
		os.close();
		is.close();
	}
	
	public void run() {
		try {
			InputStream is = url.openStream();
			File tempFile = File.createTempFile("q2-temp", null);
			tempFile.deleteOnExit();
			System.out.print("Downloading");
			copyStream(is, new FileOutputStream(tempFile), true);
			
			System.out.println("Download finished; uncompressing");
			
			ZipFile zipFile = new ZipFile(tempFile);
			Enumeration e = zipFile.entries();
			while (e.hasMoreElements()) {
				ZipEntry entry = (ZipEntry)e.nextElement();
				String name = entry.getName();
				int i;
				if ((i = name.indexOf("/baseq2")) > -1 && name.indexOf(".dll") == -1) {
					File outFile = new File("raw", name.substring(i));
					if (entry.isDirectory()) {
						outFile.mkdirs();
					} else {
						System.out.println("installing " + name.substring(i));
						outFile.getParentFile().mkdirs();
						copyStream(zipFile.getInputStream(entry), 
								new FileOutputStream(outFile), false);
					}
				}
			}
			ok = true;
			
		} catch (IOException e) {
			System.out.println("Donwload Failed");
		}
	}
}