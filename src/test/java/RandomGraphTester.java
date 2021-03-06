import java.io.File;
import java.util.Objects;
import java.util.Random;

/**
 * Tests graphs generated by RandomDotGraphGenerator. Also deletes the graphs generated
 * when the program finishes execution.
 */
public class RandomGraphTester {

    /**
     * Tests all the random graphs generated by RandomDotGraphGenerator.
     * @param args Command line arguments. This is currently not utilised.
     */
    public static void main(String[] args) {
        SolutionValidator validator = new SolutionValidator();
        Random randomGenerator = new Random();

        File currentDir = new File(System.getProperty("user.dir"));
        // tests all dot graphs that start with the name RandomGraph
        for (File file: Objects.requireNonNull(currentDir.listFiles())) {
            if (file.getName().matches("RandomGraph.*\\.dot")) {
                // only process dot files created from our generator
                int numProcessors = 1 + randomGenerator.nextInt(4);
                System.out.println("Testing " + file.getName() + " on " + numProcessors + " processors.");
                String outputFileName = file.getName().replace(".dot", "-output.dot");

                try {
                    Process process = Runtime.getRuntime().exec("java -jar scheduler.jar " + file.getName()
                            + " " + numProcessors + " -o " + outputFileName);
                    process.waitFor();
                } catch (Exception e) {
                    System.err.println("Error waiting for scheduler to run programs");
                    e.printStackTrace();
                }

                if (validator.validate(file.getName(), outputFileName, numProcessors)) {
                    System.out.println("Validated.");
                } else {
                    System.out.println("The output is not valid!!");
                }

                // clean up by deleting generated random graphs.
                file.delete();
                new File(outputFileName).delete();
            }
        }
    }
}
