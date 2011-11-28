package org.apache.cassandra.contrib.fs.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.PropertyConfigurator;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;

import org.apache.cassandra.contrib.fs.CassandraFileSystem;
import org.apache.cassandra.contrib.fs.IFileSystem;
import org.apache.cassandra.contrib.fs.Path;
import org.apache.cassandra.contrib.fs.PathUtil;
import org.apache.cassandra.contrib.fs.util.Bytes;
import org.apache.cassandra.contrib.fs.util.HDFSFileSystem;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.thrift.transport.TTransportException;

public class FSCliMain {

    private static final String Prompt = "fs> ";
    private ConsoleReader reader;
    private IFileSystem fs;
    private String curWorkingDir;
    private PrintStream out = System.out;

    public FSCliMain() throws IOException, TTransportException {
        this.reader = new ConsoleReader();
        ArgumentCompletor completor = new ArgumentCompletor(new Completor[]{
                    new FSComamndCompletor(), new FSPathCompleter(this)});
        this.reader.addCompletor(completor);
        this.reader.setBellEnabled(false);
    }

    public void setFileSystem(IFileSystem fs) {
        this.fs = fs;
    }

    public IFileSystem getFileSystem() {
        return this.fs;
    }

    public String getCWD() {
        return this.curWorkingDir;
    }

    public void setCWD(String cwd) {
        this.curWorkingDir = cwd;
    }

    public void connect() throws TTransportException, IOException {
        this.fs = CassandraFileSystem.getInstance();
        String os = System.getProperty("os.name");
        if (os.toLowerCase().contains("windows")) {
            this.curWorkingDir = "/usr/" + System.getenv("USERNAME");
        } else {
            this.curWorkingDir = "/usr/" + System.getenv("USER");
        }
        this.fs.mkdir(curWorkingDir);
    }

    public void run() throws IOException, TTransportException {
        connect();

        out.println("Welcome to cassandra fs!");
        out.println("Type 'help' for help. Type 'quit' or 'exit' to quit.");
        String line = null;
        while ((line = reader.readLine("fs@ " + curWorkingDir + ">")) != null
                && !line.equalsIgnoreCase("quit")
                && !line.equalsIgnoreCase("exit")) {
            processCommand(line);
        }
    }

    public void processCommand(String command) {
        String[] tokens = parseCommand(command);
        String cmd = tokens[0];
        try {
            if (cmd.equalsIgnoreCase("ls")) {
                processLs(tokens);
            } else if (cmd.equalsIgnoreCase("mkdir")) {
                processMkDir(tokens);
            } else if (cmd.equalsIgnoreCase("copyfromlocal")) {
                processCopyFromLocal(tokens);
            } else if (cmd.equalsIgnoreCase("copytolocal")) {
                processCopyToLocal(tokens);
            } else if (cmd.equalsIgnoreCase("copyfromhdfs")) {
                processCopyFromHDFS(tokens);
            } else if (cmd.equalsIgnoreCase("copytohdfs")) {
                processCopyToHDFS(tokens);
            } else if (cmd.equalsIgnoreCase("newfile")) {
                processNewFile(tokens);
            } else if (cmd.equalsIgnoreCase("rm")) {
                processRM(tokens);
            } else if (cmd.equalsIgnoreCase("rmr")) {
                processRMR(tokens);
            } else if (cmd.equalsIgnoreCase("cat")) {
                processCat(tokens);
            } else if (cmd.equalsIgnoreCase("pwd")) {
                out.println(curWorkingDir);
            } else if (cmd.equalsIgnoreCase("cd")) {
                processCD(tokens);
            } else if (cmd.equalsIgnoreCase("touch")) {
                processTouch(tokens);
            } else if (cmd.equalsIgnoreCase("help")) {
                processHelp(tokens);
            } else if (cmd.equalsIgnoreCase("clear")) {
                reader.clearScreen();
            } else {
                out.println("Can not recognize command '" + cmd + "'");
            }
        } catch (IOException e) {
            System.err.println(tokens[0] + ":" + e.getLocalizedMessage());
        }
    }

    private void processCopyToHDFS(String[] tokens) throws IOException {
        if (tokens.length != 3) {
            out.println("copyToHDFS <source> <dest>");
        } else {
            String hdfsURI = getHDFSURI(tokens[2]);
            FileSystem hdfsFS = HDFSFileSystem.getFileSystem(hdfsURI);
            visitNodeWhenCopyToHDFS(hdfsFS, tokens[1], tokens);
        }
    }

    private void visitNodeWhenCopyToHDFS(FileSystem hdfsFS, String source,
            String[] tokens) throws IOException {
        if (fs.exist(source)) {
            if (fs.existFile(source)) {
                OutputStream outS = hdfsFS.create(new org.apache.hadoop.fs.Path(
                        tokens[2] + strSubtract(source, tokens[1])));
                InputStream in = fs.readFile(source);
                org.apache.hadoop.io.IOUtils.copyBytes(in, outS,
                        new Configuration());

                in.close();
                outS.close();
            } else {
                List<Path> children = fs.list(source);
                for (Path child : children) {
                    visitNodeWhenCopyToHDFS(hdfsFS, child.getURL(), tokens);
                }
            }
        } else {
            out.println("The source '" + tokens[1] + "' does not exist!");
        }

    }

    private void processCopyFromHDFS(String[] tokens) throws IOException {
        if (tokens.length != 3) {
            out.println("copyFromHDFS <source> <dest>");
        } else {
            String hdfsURI = getHDFSURI(tokens[1]);
            FileSystem hdfsFS = HDFSFileSystem.getFileSystem(hdfsURI);
            visitNodeWhenCopyFromHDFS(hdfsFS, tokens[1], tokens);
        }
    }

    private void visitNodeWhenCopyFromHDFS(FileSystem hdfsFS, String source,
            String[] tokens) throws IOException {
        org.apache.hadoop.fs.Path sourcePath = new org.apache.hadoop.fs.Path(
                source);
        if (hdfsFS.exists(sourcePath)) {
            if (hdfsFS.isFile(sourcePath)) {
                fs.createFile(tokens[2] + strSubtract(source, tokens[1]),
                        hdfsFS.open(sourcePath));
            } else {
                FileStatus[] children = hdfsFS.listStatus(sourcePath);
                for (FileStatus child : children) {
                    visitNodeWhenCopyFromHDFS(hdfsFS, child.getPath().toString(), tokens);
                }
            }
        } else {
            out.println("The source '" + tokens[1] + "' does not exist!");
        }
    }

    private String getHDFSURI(String url) throws IOException {
        if (url.toLowerCase().startsWith("hdfs://")) {
            int index = url.indexOf("/", 7);
            return url.substring(0, index);
        } else if (url.toLowerCase().startsWith("file:///")) {
            return "file:///";
        }
        throw new IOException("Invalide hdfs path:" + url);
    }

    // TODO handle more complex cases,such as spaces, quotation, check path
    // validity
    public String[] parseCommand(String command) {
        return command.split("\\s+");
    }

    private void processHelp(String[] tokens) {
        out.println("List of all FS-CLI commands:");
        out.println("cd <folder>");
        out.println("pwd");
        out.println("copyToLocal <source> <dest>");
        out.println("touch <file>...");
        out.println("rm <file | folder>...");
        out.println("rmr <file | folder>...");
        out.println("newfile <file> <content>");
        out.println("cat <file>...");
        out.println("copyFromLocal <source> <dest>");
        out.println("copyFromHDFS <source> <dest>");
        out.println("copyToHDFS <source> <dest>");
        out.println("mkdir <path>");
        out.println("ls <path>");
    }

    private void processCD(String[] tokens) throws IOException {
        if (tokens.length != 2) {
            out.println("Usage: cd <folder>");
        } else {
            if (!fs.existDir(decoratePath(tokens[1]))) {
                out.println("cd " + tokens[1] + " : No such folder");
            } else {
                curWorkingDir = decoratePath(tokens[1]);
            }
        }
    }

    private void processCopyToLocal(String[] tokens)
            throws FileNotFoundException, IOException {
        if (tokens.length != 3) {
            out.println("Usage: copyToLocal <source> <dest>");
        } else {
            if (fs.existFile(decoratePath(tokens[1]))) {

                File localDestFile = new File(tokens[2]);
                if (!localDestFile.exists()) {
                    localDestFile.createNewFile();
                }

                boolean isDir = localDestFile.isDirectory();
                if (localDestFile.isFile()) {
                    InputStream in = fs.readFile(decoratePath(tokens[1]));
                    FileOutputStream os = new FileOutputStream(localDestFile);
                    IOUtils.copy(in, os);
                    os.close();
                    in.close();
                } else if (isDir) {
                    InputStream in = fs.readFile(decoratePath(tokens[1]));
                    FileOutputStream os = new FileOutputStream(localDestFile.getAbsolutePath()
                            + "/"
                            + new Path(decoratePath(tokens[1])).getName());

                    IOUtils.copy(in, os);
                    os.close();
                    in.close();
                } else {
                    System.out.println("Path neither File nor Directory...");
                }
            } else if (fs.existDir(decoratePath(tokens[1]))){
                File localFile = new File(decoratePath(tokens[2]));
                if (localFile.exists() && localFile.list().length != 0) {
                    out.println("Local dest folder '" + tokens[2]
                            + "' is not empty");
                } else {
                    localFile.mkdirs();
                    List<Path> paths = fs.list(decoratePath(tokens[1]));
                    for (Path path : paths) {
                        visitNodeWhenCopyToLocal(path, tokens);
                    }
                }
            }
            else
            {
                out.println("No such file or folder : " + tokens[1]);
            }
        }
    }

    private void visitNodeWhenCopyToLocal(Path path, String[] tokens)
            throws IOException {
        if (path.isDir()) {
            new File(decoratePath(tokens[2]
                    + strSubtract(path.getURL(), decoratePath(tokens[1])))).mkdirs();
            List<Path> subPaths = fs.list(path.getURL());
            for (Path subPath : subPaths) {
                visitNodeWhenCopyToLocal(subPath, tokens);
            }
        } else {
            InputStream in = fs.readFile(decoratePath(path.getURL()));
            IOUtils.copy(in, new FileOutputStream(new File(
                    decoratePath(tokens[2]
                    + strSubtract(path.getURL(), tokens[1])))));
            in.close();
        }
    }

    private void processTouch(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            out.println("Usage: touch <file>...");
        } else {
            if (fs.existDir(decoratePath(tokens[1]))) {
                out.println("Destination path is a folder");
                return;
            }

            for (int i = 1; i < tokens.length; ++i) {
                fs.touchFile(decoratePath(tokens[i]));
            }
        }
    }

    private void processRM(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            out.println("Usage: rm <file | folder>...");
        } else {
            for (int i = 1; i < tokens.length; ++i) {
                if (fs.existFile(decoratePath(tokens[i]))) {
                    fs.deleteFile(decoratePath(tokens[i]));
                } else if (fs.existDir(decoratePath(tokens[i]))) {
                    if (fs.countDirChilden(decoratePath(tokens[i])) != 0) {
                        out.println("rm: " + tokens[i]
                                + ": The folder is not empty");
                    } else {
                        fs.deleteDir(decoratePath(tokens[1]), false);
                    }
                } else {
                    out.println("rm: " + tokens[i]
                            + " : No such file or folder");
                }
            }
        }
    }

    private void processRMR(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            out.println("Usage: rmr <file | folder>...");
        } else {
            for (int i = 1; i < tokens.length; ++i) {
                if (fs.existFile(decoratePath(tokens[i]))) {
                    fs.deleteFile(decoratePath(tokens[i]));
                } else if (fs.existDir(decoratePath(tokens[i]))) {
                    fs.deleteDir(decoratePath(tokens[1]), true);
                } else {
                    out.println("rmr: " + tokens[i]
                            + " : No such file or folder");
                }
            }
        }

    }

    private void processNewFile(String[] tokens) throws IOException {
        //for(int i=0; i<tokens.length; i++)
  
        if (tokens.length < 3) {
            out.println("Usage: newfile <file> <content>");
        } else {
            if (fs.existDir(decoratePath(tokens[1]))) {
                out.println("Destination path is a folder");
                return;
            }

            String content = tokens[2];
            for (int i = 3; i < tokens.length; i++) {
                content += " " + tokens[i];
            }

            fs.createFile(decoratePath(tokens[1]), Bytes.toBytes(content));
        }
    }

    private void processCat(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            out.println("Usage: cat <file>...");
        } else {
            for (int i = 1; i < tokens.length; ++i) {
                if (fs.existFile(decoratePath(tokens[i]))) {
                    String content = IOUtils.toString(fs.readFile(decoratePath(tokens[i])));
                    out.println(content);
                } else {
                    out.println("cat: " + tokens[i] + ": No such file");
                }
            }
        }
    }

    private void processCopyFromLocal(String[] tokens) throws IOException,
            FileNotFoundException {
        if (tokens.length != 3) {
            out.println("Usage: copyFromLocal <source> <dest>");
        } else {
            if (fs.existDir(decoratePath(tokens[2]))) {
                out.println("Destination path is a folder");
                return;
            }
            File localFile = new File(decoratePath(tokens[1]));
            if (localFile.exists()) {
                visitNodeWhenCopyFromLocal(localFile, tokens);
            } else {
                out.println("Source '" + tokens[1] + "' does not exist");
            }
        }
    }

    private void visitNodeWhenCopyFromLocal(File file, String[] tokens)
            throws FileNotFoundException, IOException {
        if (file.isFile()) {
            copyFileFromLocal(decoratePath(file.getAbsolutePath()),
                    decoratePath(tokens[2]
                    + strSubtract(decoratePath(file.getAbsolutePath()),
                    decoratePath(tokens[1]))));
        } else {
            File[] files = file.listFiles();
            for (File child : files) {
                if (child.isFile()) {
                    copyFileFromLocal(decoratePath(child.getAbsolutePath()),
                            decoratePath(tokens[2]
                            + strSubtract(decoratePath(child.getAbsolutePath()),
                            decoratePath(tokens[1]))));
                } else {
                    visitNodeWhenCopyFromLocal(child, tokens);
                }
            }
        }
    }

    private String strSubtract(String str1, String str2) {
        int index = str1.indexOf(str2);
        if (index == 0) {
            return str1.substring(str2.length());
        } else {
            throw new RuntimeException("Can not subtract '" + str2 + "' from '"
                    + str1 + "'");
        }
    }

    private void copyFileFromLocal(String source, String dest)
            throws FileNotFoundException, IOException {
        //System.out.println("source: " + source + ", dest: " + dest);
        FileInputStream in = new FileInputStream(source);
        fs.createFile(decoratePath(dest), in);
        in.close();
    }

    private void processMkDir(String[] tokens) throws IOException {
        if (tokens.length == 1) {
            out.println("Usage: mkdir <path>");
        } else {
            for (int i = 1; i < tokens.length; ++i) {
                if (fs.exist(decoratePath(tokens[i]))) {
                    out.println("Path already exists");
                    return;
                }
                fs.mkdir(decoratePath(tokens[i]));
            }
        }
    }

    private void processLs(String[] tokens) throws IOException {
        List<String> lsDirs = new ArrayList<String>();
        if (tokens.length == 1) {
            lsDirs.add(decoratePath("."));
        } else {
            for (int i = 1; i < tokens.length; ++i) {
                lsDirs.add(decoratePath(tokens[i]));
            }
        }
        for (String lsDir : lsDirs) {
            List<Path> children = fs.list(lsDir);
            out.println("Found " + children.size() + " items");
            for (Path child : children) {
                out.println(child);
            }
        }
    }

    private String decoratePath(String path) {
        path = PathUtil.normalizePath(path);
        // transform windows path, remove the driver part
        if (path.length() >= 2 && path.charAt(1) == ':' && path.charAt(0) < 'z'
                && path.charAt(0) > 'A') {
            path = path.substring(2);
        }

        if (path.equals(".")) {
            return curWorkingDir;
        } else if (path.equals("..")) {
            if (curWorkingDir.equals("/")) {
                return "/";
            }
            int index = curWorkingDir.lastIndexOf("/");
            if (index == 0) {
                return "/";
            } else if (index != -1) {
                return curWorkingDir.substring(0, index);
            } else {
                throw new RuntimeException();
            }
        } else if (path.startsWith("/")) {
            return path;
        } else {
            return curWorkingDir + "/" + path;
        }
    }

    public static void main(String[] args) throws IOException,
            TTransportException {
        PropertyConfigurator.configure("log4j.properties");

        FSCliMain cli = new FSCliMain();
        cli.run();
        //  cli.connect();

        //  cli.processCopyToLocal(cli.parseCommand("copyToLocal /usr/svn.rar D:\\cleanup2.exe"));

//        cli.processRM(cli.parseCommand("rm /usr/ftylitak/rar.rar"));

        //cli.processCopyFromLocal(cli.parseCommand("copyFromLocal C:\\CassandraFsClient_SVN.rar /usr/ftylitak/rar.rar"));
        // cli.processLs(cli.parseCommand("ls"));

        //System.in.read();

        System.exit(0);
    }
}
