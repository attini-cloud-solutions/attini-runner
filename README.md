# attini-runner Project

The Attini runner is a small java app built with the [Quarkus framework](https://quarkus.io/).
Its purpose is to execute shell commands for the Attini Frameworks AttiniRunnerJob step.
It is also used by other steps, such as the AttiniCdk and AttiniSam step. 
For more information about the runner, 
visit the [documentation](https://docs.attini.io/api-reference/attini-runner).

## Building the Runner
The Runner is meant to be compiled in to a native image using GraalVM.

In order to build the Runner, run the following command:

```shell script
./mvnw clean package -Pnative
```

If you aren't using Linux, or don't have GraalVM installed, quarkus support container builds:

```shell script
./mvnw clean package -Pnative -Dquarkus.native.container-build=true  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-native-image:22.3-java17
```

## Using your own Runner
The Attini framework allows you to specify your own custom image when configuring a Runner.
If there is no runner app installed on the image, Attini will install it.
To use your own Runner build, you can simply use your own image with a Runner app already installed.
When installing the runner on your own image, make sure to keep the name ```attini-runner``` or the framework
will install a new version. The framework will start the app, so it only needs to be installed.

For more information about using your own image,
please visit the [Runner documentation](https://docs.attini.io/api-reference/attini-runner).
