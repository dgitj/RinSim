package rinde.sim.core.model.road;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import javax.measure.Measure;
import javax.measure.converter.UnitConverter;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.TimeLapseFactory;
import rinde.sim.core.graph.Graph;
import rinde.sim.core.graph.MultiAttributeData;
import rinde.sim.core.graph.Point;
import rinde.sim.core.graph.TestMultimapGraph;
import rinde.sim.core.graph.TestTableGraph;
import rinde.sim.util.TimeUnit;

/**
 * Test for graph with speed limits
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
 * 
 */
@RunWith(Parameterized.class)
public class SpeedLimitsTest {
  final static double DELTA = 0.00001;

  Class<? extends Graph<MultiAttributeData>> graphType;
  Class<? extends GraphRoadModel> roadModelType;
  GraphRoadModel model;
  Queue<Point> path;
  Point A, B, C, D, E;

  private final double speed;
  private double pathLength;

  @BeforeClass
  public static void assertionCheck() {
    boolean assertionsAreOn = false;
    try {
      assert false;
    } catch (final AssertionError ae) {
      assertionsAreOn = true;
    }
    assertTrue(assertionsAreOn);
  }

  public SpeedLimitsTest(Class<? extends Graph<MultiAttributeData>> pGraphType,
      Class<? extends GraphRoadModel> pRoadModelType, double pSpeed) {
    graphType = pGraphType;
    roadModelType = pRoadModelType;
    speed = pSpeed;
  }

  @Parameters
  public static Collection<Object[]> configs() {

    final double five = 5;
    final double twoAndHalf = 2.5;
    return Arrays.asList(new Object[][] {
        { TestMultimapGraph.class, GraphRoadModel.class, five },
        { TestMultimapGraph.class, CachedGraphRoadModel.class, five },
        { TestMultimapGraph.class, GraphRoadModel.class, twoAndHalf },
        { TestMultimapGraph.class, CachedGraphRoadModel.class, twoAndHalf },
        { TestTableGraph.class, GraphRoadModel.class, five },
        { TestTableGraph.class, CachedGraphRoadModel.class, five },
        { TestTableGraph.class, GraphRoadModel.class, twoAndHalf },
        { TestTableGraph.class, CachedGraphRoadModel.class, twoAndHalf } });
  }

  @Before
  public void setUp() throws InstantiationException, IllegalAccessException,
      IllegalArgumentException, SecurityException, InvocationTargetException,
      NoSuchMethodException {

    final Graph<MultiAttributeData> graph = graphType.newInstance();

    model = roadModelType.getConstructor(Graph.class, Unit.class, Unit.class)
        .newInstance(graph, SI.KILOMETER, NonSI.KILOMETERS_PER_HOUR);

    A = new Point(0, 0);
    B = new Point(0, 10);
    C = new Point(10, 0);
    D = new Point(10, 10);
    E = new Point(5, 15);

    // length 10 no speed limit
    graph.addConnection(A, B);

    // length 10 speed 2.5
    graph.addConnection(B, C, new MultiAttributeData(10d, 2.5));
    graph.addConnection(C, B); // length Math.sqr(10^2 + 10^2)

    // length 10 speed 10
    graph.addConnection(B, D, new MultiAttributeData(10d, 10));

    graph.addConnection(C, D); // length 10

    // length 12 speed 1
    graph.addConnection(D, C, new MultiAttributeData(12, 1));
    graph.addConnection(D, E, new MultiAttributeData(5, 7));

    final Set<Point> points = graph.getNodes();
    assertEquals(5, points.size());
    assertTrue(points.contains(A));
    assertTrue(points.contains(B));
    assertTrue(points.contains(C));
    assertTrue(points.contains(D));
    assertTrue(points.contains(E));

    assertEquals(7, model.getGraph().getNumberOfConnections());
    assertEquals(5, model.getGraph().getNumberOfNodes());

    path = new LinkedList<Point>();
    path.addAll(asList(A, B, C, D, E));

    pathLength = 10 + 10 + 10 + 5;
  }

  @Test
  public void followPathAllAtOnce() {
    final int timeNeeded = (int) (TimeUnit.H.toMs((long) pathLength) / speed * 1.5);
    final TimeLapse timeLapse = TimeLapseFactory.create(0, timeNeeded);

    final SpeedyRoadUser agent = new SpeedyRoadUser(speed);
    model.addObjectAt(agent, new Point(0, 0));
    assertEquals(new Point(0, 0), model.getPosition(agent));

    assertEquals(5, path.size());
    final MoveProgress travelled = model.followPath(agent, path, timeLapse);

    assertTrue(timeLapse.hasTimeLeft());
    assertEquals(pathLength, travelled.distance.getValue(), DELTA);
    assertTrue("time spend < timeNeeded", timeNeeded > travelled.time.getValue());
    assertEquals(0, path.size());
    assertEquals(new Point(5, 15), model.getPosition(agent));
  }

  /**
   * Simplest check for time based following path.
   * {@link RoadModel#followPath(MovingRoadUser, Queue, TimeLapse)}
   */
  @Test
  public void followPath() {
    assertEquals(5, path.size());

    final MovingRoadUser agent = new SpeedyRoadUser(speed);
    model.addObjectAt(agent, new Point(0, 0));
    assertTrue(model.getPosition(agent).equals(new Point(0, 0)));
    assertEquals(5, path.size());

    // MOVE 1: travelling on edge A -> B (length 10), with no speed limit
    MoveProgress progress = model.followPath(agent, path, AbstractRoadModelTest
        .hour(2));
    assertEquals(2 * speed, progress.distance.getValue(), DELTA);
    assertEquals(new Point(0, 2 * speed), model.getPosition(agent));

    if (speed < 5) {
      progress = model.followPath(agent, path, AbstractRoadModelTest.hour(2));
      assertEquals(2 * speed, progress.distance.getValue(), DELTA);
    }
    assertEquals(3, path.size());
    assertEquals(B, model.getPosition(agent));

    // MOVE 2: traveling on edge B -> C (length 10) with max speed 2.5
    progress = model.followPath(agent, path, AbstractRoadModelTest.hour(2));
    assertEquals(5, progress.distance.getValue(), DELTA);
    assertEquals(new Point(5, 5), model.getPosition(agent));
    assertEquals(3, path.size());

    // traveling on edge B -> C (length 10) with max speed 2.5
    progress = model.followPath(agent, path, AbstractRoadModelTest.hour(2));
    assertEquals(5, progress.distance.getValue(), DELTA);
    assertEquals(C, model.getPosition(agent));
    assertEquals(2, path.size());

    long time = speed < 5 ? 4 : 2;

    // follow path for 2 x time
    progress = model.followPath(agent, path, AbstractRoadModelTest.hour(time));
    assertEquals(10d, progress.distance.getValue(), DELTA);
    assertEquals(Measure.valueOf(time, NonSI.HOUR).to(SI.MILLI(SI.SECOND)), progress.time);
    assertEquals(1, path.size());
    assertEquals(D, model.getPosition(agent));

    // travel with max speed of the vehicle and time longer than needed
    time = speed < 5 ? 2 : 1;

    progress = model.followPath(agent, path, AbstractRoadModelTest.hour(3));
    assertEquals(RoadTestUtil.km(5), progress.distance);
    assertEquals(Measure.valueOf(time, NonSI.HOUR).to(SI.MILLI(SI.SECOND)), progress.time);
    assertEquals(0, path.size());
    assertEquals(E, model.getPosition(agent));

  }

  @Test
  public void maxSpeedTest() {
    final UnitConverter speedConverter = NonSI.KILOMETERS_PER_HOUR
        .getConverterTo(AbstractRoadModel.internalSpeedUnit);

    final double s = speedConverter.convert(speed);

    final SpeedyRoadUser agent = new SpeedyRoadUser(speed);
    assertEquals(s, model.getMaxSpeed(agent, A, B), DELTA);
    assertEquals(speed > 2.5 ? speedConverter.convert(2.5) : s, model.getMaxSpeed(agent, B, C), DELTA);
    assertEquals(s, model.getMaxSpeed(agent, C, B), DELTA);
    assertEquals(s, model.getMaxSpeed(agent, B, D), DELTA);
    assertEquals(s, model.getMaxSpeed(agent, C, D), DELTA);
    assertEquals(speedConverter.convert(1), model.getMaxSpeed(agent, D, C), DELTA);
    assertEquals(s, model.getMaxSpeed(agent, D, E), DELTA);
  }

  @Test
  public void followPathStepByStep() {
    final SpeedyRoadUser agent = new SpeedyRoadUser(speed);
    model.addObjectAt(agent, new Point(0, 0));
    assertEquals(new Point(0, 0), model.getPosition(agent));
    assertEquals(5, path.size());

    final MoveProgress progress = model
        .followPath(agent, path, AbstractRoadModelTest.hour());
    assertEquals(speed, progress.distance.getValue(), DELTA);
    assertEquals(4, path.size());
  }

  private class SpeedyRoadUser implements MovingRoadUser {

    private final double speedRU;

    public SpeedyRoadUser(double pSpeed) {
      speedRU = pSpeed;
    }

    @Override
    public void initRoadUser(RoadModel pModel) {}

    @Override
    public double getSpeed() {
      return speedRU;
    }
  }

}
