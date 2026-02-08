package com.treloc.xtreloc.app.util;

import java.io.File;

/**
 * Utility class for launching terminal applications on different platforms.
 * Used to launch TUI and CLI modes in a terminal window when the app is
 * launched from a macOS app bundle.
 * 
 * @author K.Minamoto
 */
public class TerminalLauncher {
    
    /**
     * Launches the application in a terminal window with the specified mode.
     * 
     * @param mode the mode to launch ("TUI" or "CLI")
     * @param args additional arguments for CLI mode (e.g., "GRD config.json")
     * @return true if the terminal was successfully launched
     */
    public static boolean launchInTerminal(String mode, String... args) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        
        if (osName.contains("mac")) {
            return launchMacTerminal(mode, args);
        } else if (osName.contains("windows")) {
            return launchWindowsTerminal(mode, args);
        } else if (osName.contains("linux") || osName.contains("unix")) {
            return launchLinuxTerminal(mode, args);
        }
        
        return false;
    }
    
    /**
     * Launches the application in macOS Terminal.
     */
    private static boolean launchMacTerminal(String mode, String... args) {
        try {
            // Get the JAR file path
            String jarPath = getJarPath();
            if (jarPath == null) {
                return false;
            }
            
            // Build the command
            StringBuilder command = new StringBuilder();
            command.append("cd '").append(new File(jarPath).getParent()).append("' && ");
            command.append("java -jar '").append(jarPath).append("'");
            
            if ("TUI".equalsIgnoreCase(mode)) {
                command.append(" --tui");
            } else if ("CLI".equalsIgnoreCase(mode)) {
                command.append(" --cli");
                if (args.length > 0) {
                    for (String arg : args) {
                        command.append(" '").append(arg).append("'");
                    }
                } else {
                    command.append(" --help");
                }
            }
            
            // Use osascript to launch Terminal.app with the command
            String appleScript = String.format(
                "tell application \"Terminal\"\n" +
                "    activate\n" +
                "    do script \"%s\"\n" +
                "end tell",
                command.toString().replace("\"", "\\\"")
            );
            
            ProcessBuilder pb = new ProcessBuilder(
                "osascript", "-e", appleScript
            );
            pb.start();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to launch terminal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Launches the application in Windows terminal (cmd.exe or PowerShell).
     */
    private static boolean launchWindowsTerminal(String mode, String... args) {
        try {
            String jarPath = getJarPath();
            if (jarPath == null) {
                return false;
            }
            
            StringBuilder command = new StringBuilder();
            command.append("cd /d \"").append(new File(jarPath).getParent()).append("\" && ");
            command.append("java -jar \"").append(jarPath).append("\"");
            
            if ("TUI".equalsIgnoreCase(mode)) {
                command.append(" --tui");
            } else if ("CLI".equalsIgnoreCase(mode)) {
                command.append(" --cli");
                if (args.length > 0) {
                    for (String arg : args) {
                        command.append(" \"").append(arg).append("\"");
                    }
                } else {
                    command.append(" --help");
                }
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c", "start", "cmd.exe", "/k", command.toString()
            );
            pb.start();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to launch terminal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Launches the application in Linux terminal.
     */
    private static boolean launchLinuxTerminal(String mode, String... args) {
        try {
            String jarPath = getJarPath();
            if (jarPath == null) {
                return false;
            }
            
            StringBuilder command = new StringBuilder();
            command.append("cd '").append(new File(jarPath).getParent()).append("' && ");
            command.append("java -jar '").append(jarPath).append("'");
            
            if ("TUI".equalsIgnoreCase(mode)) {
                command.append(" --tui");
            } else if ("CLI".equalsIgnoreCase(mode)) {
                command.append(" --cli");
                if (args.length > 0) {
                    for (String arg : args) {
                        command.append(" '").append(arg).append("'");
                    }
                } else {
                    command.append(" --help");
                }
            }
            
            // Try common terminal emulators
            String[] terminals = {"gnome-terminal", "xterm", "konsole", "x-terminal-emulator"};
            for (String terminal : terminals) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        terminal, "-e", "bash", "-c", command.toString() + "; exec bash"
                    );
                    pb.start();
                    return true;
                } catch (Exception e) {
                    // Try next terminal
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Failed to launch terminal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Gets the path to the JAR file.
     * Handles both regular JAR files and app bundle scenarios.
     * 
     * @return JAR file path, or null if not found
     */
    private static String getJarPath() {
        // Try to get JAR path from classpath or code source
        try {
            String className = TerminalLauncher.class.getName().replace('.', '/') + ".class";
            java.net.URL url = TerminalLauncher.class.getClassLoader().getResource(className);
            if (url != null) {
                String path = url.getPath();
                
                // Handle URL encoding
                if (path.startsWith("file:")) {
                    path = path.substring(5);
                }
                
                // Decode URL encoding
                try {
                    path = java.net.URLDecoder.decode(path, "UTF-8");
                } catch (Exception e) {
                    // Ignore decoding errors
                }
                
                if (path.contains("!")) {
                    path = path.substring(0, path.indexOf("!"));
                }
                
                // Remove the class file path, get the JAR
                if (path.endsWith(".jar")) {
                    File jarFile = new File(path);
                    if (jarFile.exists()) {
                        return jarFile.getAbsolutePath();
                    }
                }
                
                // Check if we're in an app bundle
                // App bundles have structure: AppName.app/Contents/Java/jarfile.jar
                if (path.contains(".app/Contents")) {
                    // Extract the app bundle path
                    int appIndex = path.indexOf(".app/Contents");
                    String appPath = path.substring(0, appIndex + 4); // Include ".app"
                    File appDir = new File(appPath);
                    if (appDir.exists() && appDir.isDirectory()) {
                        // Look for JAR in Contents/Java/
                        File javaDir = new File(appDir, "Contents/Java");
                        if (javaDir.exists()) {
                            File[] jars = javaDir.listFiles((dir, name) -> name.endsWith(".jar") && name.contains("xTreLoc"));
                            if (jars != null && jars.length > 0) {
                                return jars[0].getAbsolutePath();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to checking classpath
        }
        
        // Fallback: check java.class.path
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && classpath.contains(".jar")) {
            String[] paths = classpath.split(File.pathSeparator);
            for (String path : paths) {
                if (path.endsWith(".jar") && path.contains("xTreLoc")) {
                    File jarFile = new File(path);
                    if (jarFile.exists()) {
                        return jarFile.getAbsolutePath();
                    }
                }
            }
        }
        
        return null;
    }
}
