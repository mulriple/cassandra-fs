package org.apache.cassandra.contrib.fs;

import org.apache.cassandra.contrib.fs.util.Helper;

public class PathUtil {

	public static String getParent(String path) {

		if (path.equals("/")) {
			return null;
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}

		int index = path.lastIndexOf("/");
		if (index == -1) {
			throw new RuntimeException(path
					+ " is a relative path which is not supported here.");
		}
		if (index == 0) {
			return "/";
		} else {
			return path.substring(0, index);
		}
	}

	public static String removeTrailingSlash(String path) {
		if (path.endsWith("/") && !path.equals("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	public static String normalizePath(String path) {
		path = path.replace("\\", "/");
		return removeTrailingSlash(path);
	}

	public static void checkDirPath(String path) {
		if (Helper.isNullOrEmpty(path)){
			throw new RuntimeException("Dir Path can not been empty or null");
		}
		if (path.contains(":")) {
			throw new RuntimeException("Path can not contains ':'");
		}
	}

	public static void checkFilePath(String path) {
		if (Helper.isNullOrEmpty(path)){
			throw new RuntimeException("File Path can not been empty or null");
		}
		if (path.contains(":")) {
			throw new RuntimeException("Path can not contains ':'");
		}
	}
}
