package com.eb.guiceconfig;

import java.lang.annotation.Annotation;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.Test;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class GuiceFromConfigurationTest {

    @Test
    public void createDependencyTreeFromConfiguration() {
        // set up some test config object, could come from some file, JAXB, etc.
        Config config = prepareSomeTestConfig();

        // create the injector from the config object that describes the dependency tree
        Injector injector = Guice.createInjector(config);

        // let's demonstrate which instances are returned from the injector
        App app = injector.getInstance(App.class);
        Key<Shuttle> shuttlesKey = Key.get(Shuttle.class, Names.named("allShuttles"));
        // each getInstance call returns a new instance because multibinders have no singleton, we could wrap it in a singleton provider
        Set<Shuttle> allShuttles = injector.getInstance(shuttlesKey.ofType(new TypeLiteral<Set<Shuttle>>(){}));
        Set<Connection> connections = injector.getInstance(Key.get(new TypeLiteral<Set<Connection>>(){}));
        app.run();
        System.out.println("all shuttles: " + allShuttles.hashCode());
        allShuttles.forEach(s -> s.run());
        System.out.println("all connections: " + connections.hashCode());
        connections.forEach(c -> c.run());
    }

    private static Config prepareSomeTestConfig() {
        Config config = new Config();
        config.connections.add(new C(1));
        config.connections.add(new C(2));
        A a1 = new A(1);
        a1.shuttles.add(new S(1,1,2));
        a1.shuttles.add(new S(2,2,1));
        config.aisles.add(a1);
        A a2 = new A(2);
        a2.shuttles.add(new S(3,1,1));
        a2.shuttles.add(new S(4,2,1));
        config.aisles.add(a2);
        return config;
    }

    public static class Config implements Module {
        public Set<A> aisles = new HashSet<>();
        public Set<C> connections = new HashSet<>();

        @Override public void configure(Binder binder) {
            System.out.println("configure");
            binder.bind(App.class);

            Multibinder<Connection> connectionsBinder = Multibinder.newSetBinder(binder, Connection.class);
            for(C c : connections) {
                Key<Connection> key = Key.get(Connection.class, new RealConnectionAnnot(c.num));
                binder.bind(key).to(Connection.class).in(Singleton.class);
                connectionsBinder.addBinding().to(key);
            }

            Multibinder<Aisle> aislesBinder = Multibinder.newSetBinder(binder, Aisle.class);
            Multibinder<Shuttle> allShuttlesBinder = Multibinder.newSetBinder(binder, Key.get(Shuttle.class, Names.named("allShuttles")));
            for(A a : aisles) {
                PrivateBinder aisleBinder = binder.newPrivateBinder();
                {
                    Key<Aisle> aisleKey = Key.get(Aisle.class, new RealAisleAnnot(a.num));
                    aisleBinder.bind(aisleKey).to(Aisle.class).in(Singleton.class);
                    aisleBinder.expose(aisleKey);
                    aislesBinder.addBinding().to(aisleKey);
                }

                Multibinder<Shuttle> shuttlesBinder = Multibinder.newSetBinder(aisleBinder, Shuttle.class);
                for (S s : a.shuttles) {
                    PrivateBinder shuttleBinder = aisleBinder.newPrivateBinder();
                    shuttleBinder.bind(BigInteger.class).toInstance(BigInteger.valueOf(a.num));
                    shuttleBinder.bind(Integer.class).toInstance(s.num);
                    Key<Shuttle> shuttleKey = Key.get(Shuttle.class, new RealShuttleAnnot(a.num, s.num));
                    shuttleBinder.bind(shuttleKey).to(Shuttle.class).in(Singleton.class);
                    shuttleBinder.expose(shuttleKey);
                    aisleBinder.expose(shuttleKey);
                    shuttlesBinder.addBinding().to(shuttleKey);
                    allShuttlesBinder.addBinding().to(shuttleKey);

                    shuttleBinder.bind(Key.get(Connection.class, Names.named("conn"))).to(Key.get(Connection.class, new RealConnectionAnnot(s.connNum)));
                }
            }
        }
    }

    /**
     * Describes an {@link Aisle} in Guice and uses the values from the {@link A} config object.
     * {@link Key} uses it to describe each instance uniquely.
     */
    public static class RealAisleAnnot implements AisleAnnot {

        private final int num;

        public RealAisleAnnot(int num) {
            this.num = num;
        }

        @Override public int num() {
            return num;
        }

        @Override public Class<? extends Annotation> annotationType() {
            return AisleAnnot.class;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            RealAisleAnnot that = (RealAisleAnnot) o;
            return num == that.num;
        }

        @Override public int hashCode() {
            return Objects.hash(num);
        }

        @Override public String toString() {
            return "Aisle{" + "num=" + num + '}';
        }
    }

    /**
     * Describes an {@link Shuttle} in Guice and uses the values from the {@link S} config object.
     * {@link Key} uses it to describe each instance uniquely.
     */
    public static class RealShuttleAnnot implements ShuttleAnnot {

        private final int aisleNum;
        private final int num;

        public RealShuttleAnnot(int aisleNum, int num) {
            this.aisleNum = aisleNum;
            this.num = num;
        }

        @Override public int aisleNum() {
            return aisleNum;
        }

        @Override public int num() {
            return num;
        }

        @Override public Class<? extends Annotation> annotationType() {
            return ShuttleAnnot.class;
        }

        @Override public String toString() {
            return "Shuttle{" + "aisleNum=" + aisleNum + ", num=" + num + '}';
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            RealShuttleAnnot that = (RealShuttleAnnot) o;
            return aisleNum == that.aisleNum && num == that.num;
        }

        @Override public int hashCode() {
            return Objects.hash(aisleNum, num);
        }
    }

    /**
     * Describes an {@link Connection} in Guice and uses the values from the {@link C} config object.
     * {@link Key} uses it to describe each instance uniquely.
     */
    public static class RealConnectionAnnot implements ConnectionAnnot {

        private final int num;

        public RealConnectionAnnot(int num) {
            this.num = num;
        }

        @Override public int num() {
            return num;
        }

        @Override public Class<? extends Annotation> annotationType() {
            return ConnectionAnnot.class;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            RealConnectionAnnot that = (RealConnectionAnnot) o;
            return num == that.num;
        }

        @Override public int hashCode() {
            return Objects.hash(num);
        }

        @Override public String toString() {
            return "Connection{" + "num=" + num + '}';
        }
    }

    /**
     * Config that describes which instances should be injected into an {@link Aisle}.
     */
    public static class A {
        public int num;
        public Set<S> shuttles = new HashSet<>();

        public A(int num) {
            this.num = num;
        }
    }

    /**
     * Config that describes which instances should be injected into a {@link Shuttle}.
     */
    public static class S {
        public int id;
        public int num;
        public int connNum;

        public S(int id, int num, int connNum) {
            this.id = id;
            this.num = num;
            this.connNum = connNum;
        }
    }

    /**
     * Config that describes which instances should be injected into a {@link Connection}.
     */
    public static class C {
        public int num;

        public C(int num){
            this.num = num;
        }
    }

    /**
     * Top-level class which is a singleton.
     * Holds all objects that are needed for kicking off the application
     * and might be needed for tear down purposes at process shutdown.
     */
    public static class App {

        private final Set<Aisle> aisles;
        private final Set<Connection> connections;

        @Inject
        public App(Set<Aisle> aisles, Set<Connection> connections) {
            this.aisles = aisles;
            this.connections = connections;
        }

        public void run() {
            System.out.println("app: " + this.hashCode());
            aisles.forEach(a -> a.run());
        }
    }

    /**
     * Intermediate-level class which holds as many shuttle instances
     * as specified in the {@link A} configuration object.
     */
    public static class Aisle {
        private final Set<Shuttle> shuttles;

        @Inject
        public Aisle(Set<Shuttle> shuttles) {
            this.shuttles = shuttles;
        }

        public void run() {
            System.out.println("aisle: " + this.hashCode());
            shuttles.forEach(s -> s.run());
        }
    }

    /**
     * Bottom-level class which holds a {@link Connection} and some other values
     * as specified in the {@link S} configuration object.
     */
    public static class Shuttle {
        private final BigInteger aisleNum; // not an int to save boilerplate of one annotation
        private final int num;
        private final Connection connection;

        @Inject
        public Shuttle(BigInteger aisleNum, int num, @Named("conn") Connection connection) {
            this.aisleNum = aisleNum;
            this.num = num;
            this.connection = connection;
        }

        public void run() {
            System.out.println("shuttle: " + this);
        }

        @Override
        public String toString() {
            return "Shuttle{" +
                    "aisleNum=" + aisleNum.intValue() +
                    ", num=" + num +
                    ", connection=" + connection.hashCode() +
                    '}';
        }
    }

    /**
     * Also top-level class which is referenced by {@link Shuttle} instances.
     * As many instances exist as specified in the {@link C} configuration object.
     */
    public static class Connection {
        public void run() {
            System.out.println("connection: " + this.hashCode());
        }
    }
}
