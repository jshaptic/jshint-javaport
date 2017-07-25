package org.jshint.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jshint.Reg;

public final class JSHintUtils
{
	public static ShellUtils shell = new ShellUtils();
	public static PathUtils path = new PathUtils();
	public static CliUtils cli = new CliUtils();
	
	public static void reset()
	{
		shell = new ShellUtils();
		path = new PathUtils();
		cli = new CliUtils();
	}
	
	public static class ShellUtils
	{	
		//shjs.cat(path)
		public String cat(String path) throws IOException
		{
			return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		}
				
		//process.cwd()
		public String cwd()
		{
			return System.getProperty("user.dir");
		}
		
		//shjs.ls(path)
		public List<String> ls(String path)
		{
			List<String> result = new ArrayList<String>();
			DirectoryStream<Path> stream;
			try
			{
				stream = Files.newDirectoryStream(Paths.get(JSHintUtils.path.resolve(path)));
				for (Path p : stream)
				{
					result.add(p.getFileName().toString());
				}
			}
			catch (IOException e)
			{
				return result;
			}
			
			return result;
		}
		
		//shjs.test("-e", path)
		public boolean exists(String path)
		{
			return Files.exists(Paths.get(JSHintUtils.path.resolve(path)));
		}
		
		//shjs.test("-d")
		public boolean isDirectory(String path)
		{
			return Files.isDirectory(Paths.get(JSHintUtils.path.resolve(path)));
		}
	}
	
	public static class PathUtils
	{
		private boolean isWindows = (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0);
		
		// resolves . and .. elements in a path array with directory names there
		// must be no slashes or device names (c:\) in the array
		// (so also no leading and trailing slashes - it does not distinguish
		// relative and absolute paths)
		private String[] normalizeArray(String[] parts, boolean allowAboveRoot)
		{
			String[] res = {};
			for (String p : parts)
			{
				// ignore empty parts
				if (StringUtils.isEmpty(p) || p.equals(".")) continue;
				
				if (p.equals(".."))
				{
					if (res.length > 0 && !res[res.length-1].equals(".."))
					{
						res = ArrayUtils.remove(res, res.length-1);
					}
					else if (allowAboveRoot)
					{
						res = ArrayUtils.add(res, "..");
					}
				}
				else
				{
					res = ArrayUtils.add(res, p);
				}
			}
			
			return res;
		}
		
		// Regex to split a windows path into three parts: [*, device, slash,
		// tail] windows-only
		private String splitDeviceRe = "^([a-zA-Z]:|[\\\\\\/]{2}[^\\\\\\/]+[\\\\\\/]+[^\\\\\\/]+)?([\\\\\\/])?([\\s\\S]*?)$";
		
		// Regex to split the tail part of the above into [*, dir, basename, ext]
		private String splitTailRe = "^([\\s\\S]*?)((?:\\.{1,2}|[^\\\\\\/]+?|)(\\.[^.\\/\\\\]*|))(?:[\\\\\\/]*)$";
		
		// Function to split a filename into [root, dir, basename, ext]
		private String[] win32SplitPath(String filename)
		{
			// Separate device+slash from tail
			String[] result = Reg.exec(splitDeviceRe, filename);
			String device = (result.length > 1 ? StringUtils.defaultString(result[1]) : "") + (result.length > 2 ? StringUtils.defaultString(result[2]) : "");
			String tail = result.length > 3 ? StringUtils.defaultString(result[3]) : "";
			
			// Split the tail into dir, basename and extension
			String[] result2 = Reg.exec(splitTailRe, tail);
			String dir = result2.length > 1 ? StringUtils.defaultString(result2[1]) : "";
			String basename = result2.length > 2 ? StringUtils.defaultString(result2[2]) : "";
			String ext = result2.length > 3 ? StringUtils.defaultString(result2[3]) : "";
			
			return new String[]{device, dir, basename, ext};
		}
		
		private class StatPath
		{
			String device = "";
			boolean isUnc = false;
			boolean isAbsolute = false;
			String tail = "";
		}
		
		private StatPath win32StatPath(String path)
		{
			StatPath sp = new StatPath();
			
			String[] result = Reg.exec(splitDeviceRe, path);
			sp.device = result.length > 1 ? StringUtils.defaultString(result[1]): "";
			sp.isUnc = StringUtils.isNotEmpty(sp.device) && sp.device.charAt(1) != ':';
			sp.isAbsolute = sp.isUnc || (result.length > 2 && StringUtils.isNotEmpty(result[2])); // UNC paths are always absolute
			sp.tail = result.length > 3 ? StringUtils.defaultString(result[3]): "";
			return sp;
		}
		
		private String normalizeUNCRoot(String device)
		{
			return "\\\\" + device.replaceFirst("^[\\\\\\/]+", "").replaceAll("[\\\\\\/]+", "\\");
		}
		
		private String win32Resolve(String... paths)
		{
			String resolvedDevice = "";
			String resolvedTail = "";
			boolean resolvedAbsolute = false;
			boolean isUnc = false;
			
			if (paths != null)
			{
				
				for (int i = paths.length-1; i >= -1; i--)
				{
					String path = "";
					if (i >= 0)
					{
						path = paths[i];
					}
					else if (StringUtils.isEmpty(resolvedDevice))
					{
						path = shell.cwd();
					}
					else if (StringUtils.isEmpty(path) || !path.substring(0, 3).toLowerCase().equals(resolvedDevice.toLowerCase() + "\\"))
					{
						path = resolvedDevice + "\\";
					}
					
					if (StringUtils.isEmpty(path)) continue;
					
					StatPath result = win32StatPath(path);
					String device = result.device;
					isUnc = result.isUnc;
					boolean isAbsolute = result.isAbsolute;
					String tail = result.tail;
					
					if (StringUtils.isNotEmpty(device) && StringUtils.isNotEmpty(resolvedDevice) && !device.equalsIgnoreCase(resolvedDevice))
					{	
						// This path points to another device so it is not applicable
						continue;
					}
					
					if (StringUtils.isEmpty(resolvedDevice))
					{
						resolvedDevice = device;
					}
					if (!resolvedAbsolute)
					{
						resolvedTail = tail + "\\" + resolvedTail;
						resolvedAbsolute = isAbsolute;
					}
					
					if (StringUtils.isNotEmpty(resolvedDevice) && resolvedAbsolute) break;
				}
			}
			
			// Convert slashes to backslashes when `resolvedDevice` points to an UNC
			// root. Also squash multiple slashes into a single one where appropriate.
			if (isUnc)
			{
				resolvedDevice = normalizeUNCRoot(resolvedDevice);
			}
			
			// At this point the path should be resolved to a full absolute path,
			// but handle relative paths to be safe (might happen when process.cwd()
			// fails)
			
			// Normalize the tail path
			resolvedTail = StringUtils.join(normalizeArray(resolvedTail.split("[\\\\\\/]+"), !resolvedAbsolute), "\\");
			
			return StringUtils.defaultString(resolvedDevice + (resolvedAbsolute ? "\\" : "") + resolvedTail, ".");
		}
		
		private String win32Normalize(String path)
		{
			StatPath result = win32StatPath(path);
			String device = result.device;
			boolean isUnc = result.isUnc;
			boolean isAbsolute = result.isAbsolute;
			String tail = result.tail;
			boolean trailingSlash = Reg.test("[\\\\\\/]$", tail);
			
			// Normalize the tail path
			tail = StringUtils.join(normalizeArray(tail.split("[\\\\\\/]+"), !isAbsolute), "\\");
			
			if (StringUtils.isEmpty(tail) && !isAbsolute)
			{
				tail = ".";
			}
			if (StringUtils.isNotEmpty(tail) && trailingSlash)
			{
				tail += "\\";
			}
			
			// Convert slashes to backslashes when `device` points to an UNC root.
			// Also squash multiple slashes into a single one where appropriate.
			if (isUnc)
			{
				device = normalizeUNCRoot(device);
			}
			
			return device + (isAbsolute ? "\\" : "") + tail;
		}
		
		private String win32Join(String... paths)
		{
			String rootPath = "";
			String joined = "";
			if (paths != null)
			{
				
				for (int i = 0; i < paths.length; i++)
				{
					if (StringUtils.isNotEmpty(paths[i]))
					{
						if (StringUtils.isEmpty(rootPath))
							rootPath = paths[i];
						joined += paths[i] + "\\";
					}
				}
				
				if (StringUtils.isNotEmpty(joined))
					joined = joined.substring(0, joined.length()-1);
			}
			
			// Make sure that the joined path doesn't start with two slashes, because
			// normalize() will mistake it for an UNC path then.
			//
			// This step is skipped when it is very clear that the user actually
			// intended to point at an UNC path. This is assumed when the first
			// non-empty string arguments starts with exactly two slashes followed by
			// at least one more non-slash character.
			//
			// Note that for normalize() to treat a path as an UNC path it needs to
			// have at least 2 components, so we don't filter for that here.
			// This means that the user can use join to construct UNC paths from
			// a server name and a share name; for example:
			//   path.join('//server', 'share') -> '\\\\server\\share\')
			if (Reg.test("^[\\\\\\/]{2}[^\\\\\\/]", rootPath))
			{
				joined = joined.replaceFirst("^[\\\\\\/]{2,}", "\\");
			}
			
			return win32Normalize(joined);
		}
		
		private String win32Dirname(String path)
		{
			String[] result = win32SplitPath(path);
			String root = result[0];
			String dir = result[1];
			
			if (StringUtils.isEmpty(root) && StringUtils.isEmpty(dir))
			{
				// No dirname whatsoever
				return ".";
			}
			
			if (StringUtils.isNotEmpty(dir))
			{
				// It has a dirname, strip trailing slash
				dir = dir.substring(0, dir.length() - 1);
			}
			
			return root + dir;
		}
		
		// Split a filename into [root, dir, basename, ext], unix version
		// 'root' is just a slash, or nothing.
		private String splitPathRe = "^(\\/?|)([\\s\\S]*?)((?:\\.{1,2}|[^\\/]+?|)(\\.[^.\\/]*|))(?:[\\/]*)$";
		
		private String[] posixSplitPath(String filename)
		{
			String[] result = Reg.exec(splitPathRe, filename);
			return ArrayUtils.subarray(result, 1, result.length);
		}
		
		private String posixResolve(String... paths)
		{
			String resolvedPath = "";
			boolean resolvedAbsolute = false;
			
			if (paths != null)
			{
				
				for (int i = paths.length-1; i >= -1 && !resolvedAbsolute; i--)
				{
					String path = (i >= 0) ? paths[i] : shell.cwd();
					
					// Skip empty and invalid entries
					if (StringUtils.isEmpty(path)) continue;
					
					resolvedPath = path + "/" + resolvedPath;
					resolvedAbsolute = path.charAt(0) == '/';
				}
			}
			
			// At this point the path should be resolved to a full absolute path, but
			// handle relative paths to be safe (might happen when process.cwd() fails)
						
			// Normalize the tail path
			resolvedPath = StringUtils.join(normalizeArray(resolvedPath.split("/"), !resolvedAbsolute), "/");
			
			return StringUtils.defaultString((resolvedAbsolute ? "/" : "") + resolvedPath, ".");
		}
		
		private String posixNormalize(String path)
		{
			boolean isAbsolute = posixIsAbsolute(path);
			boolean trailingSlash = StringUtils.isNotEmpty(path) && path.charAt(path.length() - 1) == '/';
			
			// Normalize the path
			path = StringUtils.join(normalizeArray(path.split("/"), !isAbsolute), "/");
			
			if (StringUtils.isEmpty(path) && !isAbsolute)
			{
				path = ".";
			}
			if (StringUtils.isNotEmpty(path) && trailingSlash)
			{
				path += "\\";
			}
			
			return (isAbsolute ? "/" : "") + path;
		}
		
		private boolean posixIsAbsolute(String path)
		{
			return StringUtils.isNotEmpty(path) && path.charAt(0) == '/';
		}
		
		private String posixJoin(String... paths)
		{
			String path = "";
			if (paths != null)
			{
				
				for (int i = 0; i < paths.length; i++)
				{
					String segment = paths[i];
					if (StringUtils.isNotEmpty(segment))
					{
						if (StringUtils.isEmpty(path))
						{
							path += segment;
						}
						else
						{
							path += "/" + segment;
						}
					}
				}
			}
			
			return posixNormalize(path);
		}
		
		private String posixDirname(String path)
		{
			String[] result = posixSplitPath(path);
			String root = result[0];
			String dir = result[1];
			
			if (StringUtils.isEmpty(root) && StringUtils.isEmpty(dir))
			{
				// No dirname whatsoever
				return ".";
			}
			
			if (StringUtils.isNotEmpty(dir))
			{
				// It has a dirname, strip trailing slash
				dir = dir.substring(0, dir.length() - 1);
			}
			
			return root + dir;
		}
		
		//path.dirname(path)
		public String dirname(String path)
		{
			if (isWindows)
				return win32Dirname(path);
			else
				return posixDirname(path);
		}
		
		//path.join(...)
		public String join(String... paths)
		{
			if (isWindows)
				return win32Join(paths);
			else
				return posixJoin(paths);
		}
				
		//path.normalize(path)
		public String normalize(String path)
		{
			if (isWindows)
				return win32Normalize(path);
			else
				return posixNormalize(path);
		}
		
		public String convertSepToPosix(String value)
		{
			return value.replace("\\\\", "/").replace("\\", "/");
		}
		
		//path.resolve(...)
		public String resolve(String... paths)
		{
			if (isWindows)
				return win32Resolve(paths);
			else
				return posixResolve(paths);
		}
	}
	
	public static class CliUtils
	{
		public String readFromStdin()
		{
			String result = "";
			
			try
			{
				BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				String line;
				while ((line = in.readLine()) != null)
				{
					result += line + "\n";
				}
				return result;
			}
			catch (IOException e)
			{
				return null;
			}
		}
	}
}
