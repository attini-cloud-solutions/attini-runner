package se.attini.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class PathRegistry {

    private List<Path> paths = new ArrayList<>();

    private PathRegistry() {
    }

    public Path register(Path path){
        paths.add(path);
        return path;
    }

    public void deletePaths(){
        paths.stream()
             .map(Path::toFile)
             .forEach(FileUtils::deleteQuietly);
        paths = new ArrayList<>();
    }

    public static PathRegistry create(){
        return new PathRegistry();
    }




}
