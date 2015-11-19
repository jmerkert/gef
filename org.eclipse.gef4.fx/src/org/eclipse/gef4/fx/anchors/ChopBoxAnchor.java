/*******************************************************************************
 * Copyright (c) 2014, 2015 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.fx.anchors;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.gef4.common.adapt.AdapterKey;
import org.eclipse.gef4.common.adapt.IAdaptable;
import org.eclipse.gef4.fx.anchors.ChopBoxAnchor.IComputationStrategy.Impl;
import org.eclipse.gef4.fx.nodes.Connection;
import org.eclipse.gef4.fx.nodes.GeometryNode;
import org.eclipse.gef4.fx.utils.NodeUtils;
import org.eclipse.gef4.geometry.convert.fx.Geometry2JavaFX;
import org.eclipse.gef4.geometry.convert.fx.JavaFX2Geometry;
import org.eclipse.gef4.geometry.planar.BezierCurve;
import org.eclipse.gef4.geometry.planar.ICurve;
import org.eclipse.gef4.geometry.planar.IGeometry;
import org.eclipse.gef4.geometry.planar.IShape;
import org.eclipse.gef4.geometry.planar.Line;
import org.eclipse.gef4.geometry.planar.Point;

import com.sun.javafx.collections.ObservableMapWrapper;
import com.sun.javafx.geom.Rectangle;

import javafx.beans.property.ReadOnlyMapWrapper;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;

/**
 * The {@link ChopBoxAnchor} computes anchor positions based on a reference
 * position per anchored and one reference position for the anchorage. The
 * anchoreds' reference positions are provided when
 * {@link #attach(AnchorKey, IAdaptable) attaching} an {@link AnchorKey}. The
 * computation is carried out by a {@link IComputationStrategy}. The default
 * computation strategy ({@link Impl}) will connect anchored and anchorage
 * reference position and compute the intersection with the outline of the
 * anchorage.
 *
 * @author anyssen
 * @author mwienand
 *
 */
// TODO: Find an appropriate name for this (outline anchor or shape anchor or
// perimeter anchor)
// It has nothing to do with a ChopBox, so this does not seem to be intuitive.
public class ChopBoxAnchor extends AbstractAnchor {

	/**
	 * The {@link IComputationStrategy} is responsible for computing anchor
	 * positions based on an anchorage {@link Node}, an anchored {@link Node},
	 * and an anchored reference position (
	 * {@link #computePositionInScene(Node, Node, Point)}).
	 */
	public interface IComputationStrategy {

		/**
		 * The default implementation of the {@link IComputationStrategy}
		 * computes an anchor position as follows:
		 * <ol>
		 * <li>Compute the anchorage geometry based on its visual (
		 * {@link #getAnchorageReferenceGeometryInLocal(Node)}).</li>
		 * <li>Compute an anchorage reference position based on its geometry (
		 * {@link #computeAnchorageReferencePointInLocal(Node, IGeometry)}).
		 * </li>
		 * <li>Transform this reference position into the coordinate system of
		 * the scene (
		 * {@link #computeAnchorageReferencePointInScene(Node, IGeometry)}).
		 * </li>
		 * <li>Connect anchored and anchorage reference positions.</li>
		 * <li>Compute the intersection of the connection and the outline of the
		 * anchorage geometry ({@link #getOutline(IGeometry)}).</li>
		 * </ol>
		 */
		public class Impl implements IComputationStrategy {

			/**
			 * Computes the anchorage reference position within the coordinate
			 * system of the given {@link IGeometry}. For an {@link IShape}
			 * geometry, the center is used if it is contained within the shape,
			 * otherwise, the vertex nearest to the center is used as the
			 * reference position. For an {@link ICurve} geometry, the first
			 * point is used as the reference position.
			 *
			 * @param node
			 *            The anchorage visual.
			 * @param geometryInLocal
			 *            The anchorage geometry within the local coordinate
			 *            system of the anchorage visual.
			 * @return A position within the given {@link IGeometry}.
			 */
			// TODO: reduce visibility
			public Point computeAnchorageReferencePointInLocal(Node node,
					IGeometry geometryInLocal) {
				// TODO: we cannot handle Path yet
				if (!(geometryInLocal instanceof IShape)
						&& !(geometryInLocal instanceof ICurve)) {
					// TODO: Path
					throw new IllegalArgumentException(
							"The given IGeometry is neither an IShape nor an ICurve.");
				}

				// determine the bounds center
				Point boundsCenterInLocal = geometryInLocal.getBounds()
						.getCenter();

				// if the bounds center is contained, it is good enough as a
				// reference point
				if (!geometryInLocal.contains(boundsCenterInLocal)) {
					// otherwise we have to search for another reference point
					if (geometryInLocal instanceof IShape) {
						// in case of an IShape we can pick the vertex nearest
						// to
						// the center point
						Point nearestVertex = getNearestVertex(
								boundsCenterInLocal, (IShape) geometryInLocal);
						if (nearestVertex != null) {
							return nearestVertex;
						} else {
							throw new IllegalArgumentException(
									"The given IShape does not provide any vertices.");
						}
					} else {
						BezierCurve[] bezier = ((ICurve) geometryInLocal)
								.toBezier();
						return bezier[bezier.length / 2].get(0.5);
					}
					// TODO Path
				} else {
					return boundsCenterInLocal;
				}
			}

			/**
			 * Computes the anchorage reference position in scene coordinates,
			 * based on the given anchorage geometry.
			 *
			 * @see #computeAnchorageReferencePointInLocal(Node, IGeometry)
			 * @param node
			 *            The anchorage visual.
			 * @param geometryInLocal
			 *            The anchorage geometry within the coordinate system of
			 *            the anchorage visual.
			 * @return The anchorage reference position.
			 */
			protected Point computeAnchorageReferencePointInScene(Node node,
					IGeometry geometryInLocal) {
				return NodeUtils.localToScene(node,
						computeAnchorageReferencePointInLocal(node,
								geometryInLocal));
			}

			/*
			 * (non-Javadoc)
			 *
			 * @see
			 * org.eclipse.gef4.fx.anchors.AnchorageReferenceComputationStrategy
			 * # computePositionInScene(javafx.scene.Node,
			 * org.eclipse.gef4.geometry.planar.IGeometry, javafx.scene.Node,
			 * org.eclipse.gef4.geometry.planar.Point)
			 */
			@Override
			public Point computePositionInScene(Node anchorage, Node anchored,
					Point anchoredReferencePointInLocal) {
				IGeometry anchorageReferenceGeometryInLocal = getAnchorageReferenceGeometryInLocal(
						anchorage);

				Point anchoredReferencePointInScene = NodeUtils
						.localToScene(anchored, anchoredReferencePointInLocal);

				Point anchorageReferencePointInScene = computeAnchorageReferencePointInScene(
						anchorage, anchorageReferenceGeometryInLocal);

				Line referenceLineInScene = new Line(
						anchorageReferencePointInScene,
						anchoredReferencePointInScene);

				IGeometry anchorageGeometryInScene = NodeUtils.localToScene(
						anchorage, anchorageReferenceGeometryInLocal);
				ICurve anchorageOutlineInScene = getOutline(
						anchorageGeometryInScene);

				Point nearestIntersectionInScene = anchorageOutlineInScene
						.getNearestIntersection(referenceLineInScene,
								anchoredReferencePointInScene);
				if (nearestIntersectionInScene != null) {
					return nearestIntersectionInScene;
				}

				// in case of emergency, return the anchorage reference point
				return anchorageReferencePointInScene;
			}

			/**
			 * Determines the anchorage geometry based on the given anchorage
			 * visual. For an {@link GeometryNode}, the corresponding geometry
			 * is returned. Otherwise, a {@link Rectangle} representing the
			 * layout-bounds of the visual is returned.
			 *
			 * @param anchorage
			 *            The anchorage visual.
			 * @return The anchorage geometry within the local coordinate system
			 *         of the given anchorage visual.
			 */
			protected IGeometry getAnchorageReferenceGeometryInLocal(
					Node anchorage) {
				IGeometry geometry = null;
				if (anchorage instanceof Connection) {
					geometry = ((Connection) anchorage).getCurveNode()
							.getGeometry();
				} else if (anchorage instanceof GeometryNode) {
					geometry = ((GeometryNode<?>) anchorage).getGeometry();
				}
				// TODO: Path
				if (!(geometry instanceof IShape)
						&& !(geometry instanceof ICurve)) {
					geometry = JavaFX2Geometry
							.toRectangle(anchorage.getLayoutBounds());
				}
				return geometry;
			}

			/**
			 * Determines the vertex of the given {@link IShape} which is
			 * nearest to the given center {@link Point}.
			 *
			 * @param boundsCenter
			 *            The ideal anchorage reference position.
			 * @param shape
			 *            The anchorage geometry.
			 * @return The <i>shape</i> vertex nearest to the given
			 *         <i>boundsCenter</i>.
			 */
			protected Point getNearestVertex(Point boundsCenter, IShape shape) {
				ICurve[] outlineSegments = shape.getOutlineSegments();
				if (outlineSegments.length == 0) {
					return null;
				}
				// find vertex nearest to boundsCenter
				Point nearestVertex = outlineSegments[0].getP1();
				double minDistance = boundsCenter.getDistance(nearestVertex);
				for (int i = 1; i < outlineSegments.length; i++) {
					Point v = outlineSegments[i].getP1();
					double d = boundsCenter.getDistance(v);
					if (d < minDistance) {
						nearestVertex = v;
						minDistance = d;
					}
				}
				return nearestVertex;
			}

			/**
			 * Determines the outline of the given {@link IGeometry}.
			 *
			 * @param geometry
			 *            The anchorage geometry.
			 * @return The outline of the given {@link IGeometry}.
			 */
			protected ICurve getOutline(IGeometry geometry) {
				// TODO: we cannot handle Path yet
				if (!(geometry instanceof IShape)
						&& !(geometry instanceof ICurve)) {
					throw new IllegalArgumentException(
							"The given IGeometry is neither an ICurve nor an IShape.");
				}

				if (geometry instanceof IShape) {
					return ((IShape) geometry).getOutline();
				} else if (geometry instanceof ICurve) {
					return (ICurve) geometry;
				} else {
					throw new IllegalStateException(
							"The transformed geometry is neither an ICurve nor an IShape.");
				}
			}

		}

		/**
		 * Computes an anchor position based on the given anchorage visual,
		 * anchored visual, and anchored reference point.
		 *
		 * @param anchorage
		 *            The anchorage visual.
		 * @param anchored
		 *            The anchored visual.
		 * @param anchoredReferencePointInLocal
		 *            The anchored reference point within the local coordinate
		 *            system of the anchored visual.
		 * @return The anchor position.
		 */
		Point computePositionInScene(Node anchorage, Node anchored,
				Point anchoredReferencePointInLocal);

	}

	/**
	 * A {@link IReferencePointProvider} needs to be provided as default adapter
	 * (see {@link AdapterKey#get(Class)}) on the {@link IAdaptable} info that
	 * gets passed into {@link ChopBoxAnchor#attach(AnchorKey, IAdaptable)} and
	 * {@link ChopBoxAnchor#detach(AnchorKey, IAdaptable)}. The
	 * {@link IReferencePointProvider} has to provide a reference point for each
	 * {@link AdapterKey} that is attached to the {@link ChopBoxAnchor}. It will
	 * be used when computing anchor positions for the respective
	 * {@link AnchorKey}.
	 *
	 * @author anyssen
	 *
	 */
	public interface IReferencePointProvider {

		/**
		 * A simple {@link IReferencePointProvider} implementation that allows
		 * to statically set reference points for {@link AnchorKey}s.
		 *
		 */
		public class Impl implements IReferencePointProvider {

			private ObservableMap<AnchorKey, Point> referencePoints = new ObservableMapWrapper<AnchorKey, Point>(
					new HashMap<AnchorKey, Point>());

			/**
			 * Sets or updates the reference point for the given
			 * {@link AnchorKey}.
			 *
			 * @param anchorKey
			 *            The {@link AnchorKey} for which the reference point is
			 *            to be set or updated.
			 * @param referencePoint
			 *            The new reference point to set.
			 */
			public void put(AnchorKey anchorKey, Point referencePoint) {
				referencePoints.put(anchorKey, referencePoint);
			}

			@Override
			public ReadOnlyMapWrapper<AnchorKey, Point> referencePointProperty() {
				return new ReadOnlyMapWrapper<>(referencePoints);
			}
		}

		/**
		 * Provides a read-only (map) property with positions (in local
		 * coordinates of the anchored {@link Node}) for all attached
		 * {@link AnchorKey}s.
		 *
		 * @return A read-only (map) property storing reference positions for
		 *         all {@link AnchorKey}s attached to the {@link ChopBoxAnchor}s
		 *         it is forwarded to.
		 */
		public abstract ReadOnlyMapWrapper<AnchorKey, Point> referencePointProperty();

	}

	private Map<AnchorKey, IReferencePointProvider> anchoredReferencePointProviders = new HashMap<>();

	private MapChangeListener<AnchorKey, Point> anchoredReferencePointsChangeListener = new MapChangeListener<AnchorKey, Point>() {
		@Override
		public void onChanged(
				javafx.collections.MapChangeListener.Change<? extends AnchorKey, ? extends Point> change) {
			if (change.wasAdded()) {
				// Do some defensive checks here. However, if we run into null
				// key or value here, this will be an inconsistency of the
				// ChopBoxHelper#referencePointProperty()
				if (change.getKey() == null) {
					throw new IllegalStateException(
							"Attempt to put <null> key into reference point map!");
				}
				if (change.getValueAdded() == null) {
					throw new IllegalStateException(
							"Attempt to put <null> value into reference point map!");
				}
				if (anchoredReferencePointProviders
						.containsKey(change.getKey())) {
					// only recompute position, if one of our own keys changed
					// (ChopBoxHelper#referencePointProperty() may contain
					// AnchorKeys registered at other anchors as well)
					updatePosition(change.getKey());
				}
			}
		}
	};

	private IComputationStrategy computationStrategy;

	/**
	 * Constructs a new {@link ChopBoxAnchor} for the given anchorage visual.
	 * Uses the default computation strategy ({@link Impl}).
	 *
	 * @param anchorage
	 *            The anchorage visual.
	 */
	public ChopBoxAnchor(Node anchorage) {
		this(anchorage, new IComputationStrategy.Impl());
	}

	/**
	 * Constructs a new {@link ChopBoxAnchor} for the given anchorage visual
	 * using the given {@link IComputationStrategy}.
	 *
	 * @param anchorage
	 *            The anchorage visual.
	 * @param computationStrategy
	 *            The {@link IComputationStrategy} to use.
	 */
	public ChopBoxAnchor(Node anchorage,
			IComputationStrategy computationStrategy) {
		super(anchorage);
		this.computationStrategy = computationStrategy;
	}

	/**
	 * Attaches the given {@link AnchorKey} to this {@link ChopBoxAnchor}.
	 * Requires that an {@link IReferencePointProvider} can be obtained from the
	 * passed in {@link IAdaptable}.
	 *
	 * @param key
	 *            The {@link AnchorKey} to be attached.
	 * @param info
	 *            An {@link IAdaptable}, which will be used to obtain an
	 *            {@link IReferencePointProvider} that provides reference points
	 *            for this {@link ChopBoxAnchor}.
	 *
	 */
	@Override
	public void attach(AnchorKey key, IAdaptable info) {
		IReferencePointProvider referencePointProvider = info
				.getAdapter(IReferencePointProvider.class);
		if (referencePointProvider == null) {
			throw new IllegalArgumentException(
					"No IReferencePointProvider could be obtained via info.");
		}

		// we need to keep track of it, otherwise we will not be able to access
		// the reference point information (in case of other changes).
		anchoredReferencePointProviders.put(key, referencePointProvider);

		// will enforce a re-computation of positions, so we need to have
		// obtained the helper beforehand.
		super.attach(key, info);

		// add listener to reference point changes
		referencePointProvider.referencePointProperty()
				.addListener(anchoredReferencePointsChangeListener);
	}

	/**
	 * Recomputes the position for the given attached {@link AnchorKey} by
	 * retrieving a reference position via the {@link IReferencePointProvider}
	 * that was obtained when attaching the {@link AnchorKey} (
	 * {@link #attach(AnchorKey, IAdaptable)}).
	 *
	 * @param key
	 *            The {@link AnchorKey} for which to compute an anchor position.
	 */
	@Override
	protected Point computePosition(AnchorKey key) {
		Point referencePoint = anchoredReferencePointProviders.get(key)
				.referencePointProperty().get(key);
		if (referencePoint == null) {
			throw new IllegalStateException(
					"The IReferencePointProvider does not provide a reference point for this key: "
							+ key);
		}
		return computePosition(key.getAnchored(), referencePoint);
	}

	/**
	 * Computes the point of intersection between the outline of the anchorage
	 * reference shape and the line through the reference points of anchorage
	 * and anchored.
	 *
	 * @param anchored
	 *            The to be anchored {@link Node} for which the anchor position
	 *            is to be determined.
	 * @param anchoredReferencePointInLocal
	 *            A reference {@link Point} used for calculation of the anchor
	 *            position, provided within the local coordinate system of the
	 *            to be anchored {@link Node}.
	 * @return Point The anchor position within the local coordinate system of
	 *         the to be anchored {@link Node}.
	 */
	protected Point computePosition(Node anchored,
			Point anchoredReferencePointInLocal) {
		return JavaFX2Geometry.toPoint(anchored
				.sceneToLocal(Geometry2JavaFX.toFXPoint(computationStrategy
						.computePositionInScene(getAnchorage(), anchored,
								anchoredReferencePointInLocal))));
	}

	/**
	 * Detaches the given {@link AnchorKey} from this {@link ChopBoxAnchor}.
	 * Requires that an {@link IReferencePointProvider} can be obtained from the
	 * passed in {@link IAdaptable}.
	 *
	 * @param key
	 *            The {@link AnchorKey} to be detached.
	 * @param info
	 *            An {@link IAdaptable}, which will be used to obtain an
	 *            {@link IReferencePointProvider} that provides reference points
	 *            for this {@link ChopBoxAnchor}.
	 */
	@Override
	public void detach(AnchorKey key, IAdaptable info) {
		IReferencePointProvider helper = info
				.getAdapter(IReferencePointProvider.class);
		if (helper == null) {
			throw new IllegalArgumentException(
					"No ChopBoxHelper could be obtained via info.");
		}
		if (anchoredReferencePointProviders.get(key) != helper) {
			throw new IllegalStateException(
					"The passed in ChopBoxHelper had not been obtained for "
							+ key + " within attach() before.");
		}

		// unregister reference point listener
		helper.referencePointProperty()
				.removeListener(anchoredReferencePointsChangeListener);

		super.detach(key, info);

		anchoredReferencePointProviders.remove(key);
	}

}
