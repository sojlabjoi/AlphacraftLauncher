/*
 * Decompiled with CFR 0.150.
 */
package net.minecraft;

import JAR.JarInputStream;

import java.applet.Applet;
import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

public class MCUpdate implements Runnable {
    private static final Set<MCUtils.OS> platforms = Collections
            .unmodifiableSet(EnumSet.of(MCUtils.OS.osx, MCUtils.OS.linux, MCUtils.OS.windows));
    private static final String mainJarName = "client";
    private final MCUpdateDownload minecraftUpdateDownload = new MCUpdateDownload(this);
    private final MCUpdateExtract minecraftUpdateExtract = new MCUpdateExtract(this);
    private int state;
    protected int percentage;
    private int currentSizeDownload;
    private int totalSizeDownload;
    protected boolean fatalError;
    private static boolean natives_loaded = false;
    protected String fatalErrorDescription;
    protected String subtaskMessage = "";
    private String downloadSpeedMessage = "";
    private URL[] urlList;
    private ClassLoader classLoader;
    protected JarInputStream JarInputStream = new JarInputStream();

    protected void init() {
        this.state = 1;
    }

    private void loadFileURLs() {
        this.state = 2;

        String libs;
        String natives;
        switch (MCUtils.getPlatform()) {
            case osx:
                libs = "libs-osx.zip";
                natives = "natives-osx.zip";
                break;
            case linux:
                libs = "libs-linux.zip";
                natives = "natives-linux.zip";
                break;
            case windows:
                libs = "libs-windows.zip";
                natives = "natives-windows.zip";
                break;
            default:
                throw new RuntimeException();
        }
        try {
            this.urlList = new URL[] {
                    new URL("http://files.betacraft.uk/launcher/assets/" + libs),
                    new URL("http://files.betacraft.uk/launcher/assets/" + natives),
                    new URL("https://piston-data.mojang.com/v1/objects/e1c682219df45ebda589a557aadadd6ed093c86c/"
                            + mainJarName + ".jar") };
        } catch (MalformedURLException e) {
            this.fatalErrorException("No such file or archive found", e);
        }
    }

    private void updateClasspath(File dir) throws Exception {
        this.state = 6;
        this.percentage = 95;
        String[] classpath = new String[] {
                dir.getAbsolutePath() + File.separator + "jinput.jar",
                dir.getAbsolutePath() + File.separator + "lwjgl.jar",
                dir.getAbsolutePath() + File.separator + "lwjgl_util.jar",
                dir.getAbsolutePath() + File.separator + "minecraft.jar" };

        URL[] urls = new URL[classpath.length];
        int i = 0;
        do {
            urls[i] = new File(classpath[i]).toURI().toURL();
            i++;
        } while (i < classpath.length);
        if (classLoader == null) {
            classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> {
                URLClassLoader cl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
                Thread.currentThread().setContextClassLoader(cl);
                return cl;
            });
        }

        String path;
        if (platforms.contains(MCUtils.getPlatform())) {
            path = dir.getAbsolutePath() + File.separator;
        } else {
            throw new RuntimeException();
        }
        if (!natives_loaded) {
            Field field = ClassLoader.class.getDeclaredField("loadedLibraryNames");
            field.setAccessible(true);

            Vector<?> loadedLibraryNames = (Vector<?>) field.get(classLoader);
            loadedLibraryNames.clear();
        }
        Arrays.asList("org.lwjgl.librarypath", "net.java.games.input.librarypath")
                .forEach(s -> System.setProperty(s, path + "natives"));
        natives_loaded = true;
    }

    private void fatalErrorException(String s, Exception e) {
        e.printStackTrace();
        this.fatalError = true;
        this.fatalErrorDescription = "Fatal error occurred (" + this.state + "): " + s;
    }

    protected boolean canPlayOffline() {
        try {
            String path = AccessController
                    .doPrivileged((PrivilegedExceptionAction<String>) () -> MCUtils.getWorkingDirectory()
                            + File.separator + "bin" + File.separator);

            File dir = new File(path);
            return dir.exists() && Objects.requireNonNull(dir.listFiles()).length > 0
                    && new File(path + "minecraft.jar").exists();
        } catch (PrivilegedActionException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void run() {
        this.init();
        this.state = 3;
        this.percentage = 5;
        try {
            this.loadFileURLs();
            String path = AccessController
                    .doPrivileged((PrivilegedExceptionAction<String>) () -> MCUtils.getWorkingDirectory()
                            + File.separator + "bin" + File.separator);

            File dir = new File(path);
            if (!dir.exists()) {
                boolean mkdirs = dir.mkdirs();
                assert mkdirs : "Failed to read directory " + path;
            }
            if (!this.canPlayOffline()) {
                minecraftUpdateDownload.downloadFiles(path);
                minecraftUpdateExtract.extractZipArchives(path);
            }
            this.percentage = 90;
            this.updateClasspath(dir);
            this.state = 7;
        } catch (Exception e) {
            this.fatalErrorException(e.getMessage(), e);
        }
    }

    protected Applet createAppletInstance()
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        return (Applet) classLoader.loadClass("net.minecraft.client.MinecraftApplet").newInstance();
    }

    /**
     * ##################################################
     * # GETTERS & SETTERS #
     * ##################################################
     */
    protected String getState() {
        switch (this.state) {
            case 1:
                return "Initializing loader";
            case 2:
                return "Determining packages to load";
            case 3:
                return "Checking cache for existing files";
            case 4:
                return "Downloading packages";
            case 5:
                return "Extracting downloaded packages";
            case 6:
                return "Updating classpath";
            case 7:
                return "Done loading";
            default:
                return "Unknown state";
        }
    }

    protected String getFileName(URL url) {
        return url.getFile().lastIndexOf('/') != -1 ? url.getFile().substring(url.getFile().lastIndexOf('/') + 1)
                : url.getFile();
    }

    protected String getMainJarName() {
        return mainJarName;
    }

    protected JarInputStream getJarInputStream() {
        return JarInputStream;
    }

    protected String getDownloadSpeedMessage() {
        return downloadSpeedMessage;
    }

    protected URL[] getUrlList() {
        return urlList;
    }

    protected int getPercentage() {
        return this.percentage;
    }

    protected int getCurrentSizeDownload() {
        return currentSizeDownload;
    }

    protected int getTotalSizeDownload() {
        return totalSizeDownload;
    }

    protected void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    protected void setCurrentSizeDownload(int currentSizeDownload) {
        this.currentSizeDownload = currentSizeDownload;
    }

    protected void setTotalSizeDownload(int totalSizeDownload) {
        this.totalSizeDownload = totalSizeDownload;
    }

    protected void setSubtaskMessage(String subtaskMessage) {
        this.subtaskMessage = subtaskMessage;
    }

    protected void setDownloadSpeedMessage(String downloadSpeedMessage) {
        this.downloadSpeedMessage = downloadSpeedMessage;
    }

    protected void setState(int state) {
        this.state = state;
    }
}
