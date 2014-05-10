/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist-db Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.jfree.chart.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.PieSectionEntity;
import org.jfree.chart.event.PlotChangeEvent;
import org.jfree.chart.labels.PieToolTipGenerator;
import org.jfree.chart.urls.PieURLGenerator;
import org.jfree.chart.util.ParamChecks;
import org.jfree.data.general.DatasetUtilities;
import org.jfree.data.general.PieDataset;
import org.jfree.io.SerialUtilities;
import org.jfree.ui.RectangleInsets;
import org.jfree.util.ObjectUtilities;
import org.jfree.util.PaintUtilities;
import org.jfree.util.Rotation;
import org.jfree.util.ShapeUtilities;
import org.jfree.util.UnitType;

/**
 * A customised ring plot that shows sun burst partitions.
 *
 * @author Leif-Jöran Olsson (ljo@exist-db.org)
 */
public class SunburstPlot extends RingPlot implements Cloneable, Serializable {

    /** For serialization. */
    private static final long serialVersionUID = 1556064784129676621L;

    private Map<Comparable, PieDataset> burstDatasets;
    private double latestBurstAngle;
    private Line2D nextBurstSeparator;

    /**
     * Creates a new plot with a <code>null</code> dataset.
     */
    public SunburstPlot() {
        this(null, null);
    }

    /**
     * Creates a new plot for the specified dataset.
     *
     * @param dataset  the dataset (<code>null</code> permitted).
     */
    public SunburstPlot(PieDataset dataset, Map<Comparable, PieDataset> burstDatasets) {
        super(dataset);
	this.burstDatasets = burstDatasets;
	this.latestBurstAngle = 90.0;
        setSeparatorsVisible(true);
    }

    /**
     * Returns the latest burst angle.
     *
     * @return A double.
     *
     */
    public double getLatestBurstAngle() {
	return latestBurstAngle;
    }

    /**
     * Sets the latest burst angle.
     *
     * @param latestBurstAngle  the latest burst angle
     *
     */
    public void setLatestBurstAngle(final double latestBurstAngle) {
	this.latestBurstAngle = latestBurstAngle;
    }

    /**
     * Returns the next burst separator to use.
     *
     * @return A Line2D.
     *
     */
    public Line2D getNextBurstSeparator() {
	return nextBurstSeparator;
    }

    /**
     * Sets the next burst separator to use.
     *
     * @param nextBurstSeparator  the next burst separator
     *
     */
    public void setNextBurstSeparator(final Line2D nextBurstSeparator) {
	this.nextBurstSeparator = nextBurstSeparator;
    }

    /**
     * Initialises the plot state (which will store the total of all dataset
     * values, among other things).  This method is called once at the
     * beginning of each drawing.
     *
     * @param g2  the graphics device.
     * @param plotArea  the plot area (<code>null</code> not permitted).
     * @param plot  the plot.
     * @param index  the secondary index (<code>null</code> for primary
     *               renderer).
     * @param info  collects chart rendering information for return to caller.
     *
     * @return A state object (maintains state information relevant to one
     *         chart drawing).
     */
    @Override
    public PiePlotState initialise(Graphics2D g2, Rectangle2D plotArea,
            PiePlot plot, Integer index, PlotRenderingInfo info) {

        PiePlotState state = super.initialise(g2, plotArea, plot, index, info);
        state.setPassesRequired(3);
        return state;

    }

    /**
     * Draws a single data item.
     *
     * @param g2  the graphics device (<code>null</code> not permitted).
     * @param section  the section index.
     * @param dataArea  the data plot area.
     * @param state  state information for one chart.
     * @param currentPass  the current pass index.
     */
    @Override
    protected void drawItem(Graphics2D g2, int section, Rectangle2D dataArea,
			    PiePlotState state, int currentPass) {
        PieDataset dataset = getDataset();
        Number n = dataset.getValue(section);
        if (n == null) {
            return;
        }
        double value = n.doubleValue();
        double angle1 = 0.0;
        double angle2 = 0.0;
	int level = 1;
	
        Rotation direction = getDirection();
        if (direction == Rotation.CLOCKWISE) {
            angle1 = state.getLatestAngle();
            angle2 = angle1 - value / state.getTotal() * 360.0;
        }
        else if (direction == Rotation.ANTICLOCKWISE) {
            angle1 = state.getLatestAngle();
            angle2 = angle1 + value / state.getTotal() * 360.0;
        }
        else {
            throw new IllegalStateException("Rotation type not recognised.");
        }
	
        double angle = (angle2 - angle1);
	
        if (Math.abs(angle) > getMinimumArcAngleToDraw()) {
            Comparable key = getSectionKey(section);
            double ep = 0.0;
            double mep = getMaximumExplodePercent();
            if (mep > 0.0) {
                ep = getExplodePercent(key) / mep;
            }
            Rectangle2D arcBounds = 
		getArcBounds(state.getPieArea(), state.getExplodedPieArea(), angle1,
			     angle, ep);
            Arc2D.Double arc = new Arc2D.Double(arcBounds, angle1, angle, Arc2D.OPEN);
            // create the bounds for the inner arc
            double depth = getSectionDepth() / 1.67;
            RectangleInsets s = new RectangleInsets(UnitType.RELATIVE,
						    depth, depth, depth, depth);
            Rectangle2D innerArcBounds = new Rectangle2D.Double();
            innerArcBounds.setRect(arcBounds);
            s.trim(innerArcBounds);
            // calculate inner arc in reverse direction, for later
            // GeneralPath construction
            Arc2D.Double arc2 = new Arc2D.Double(innerArcBounds, angle1
						 + angle, -angle, Arc2D.OPEN);
            GeneralPath path = new GeneralPath();
            path.moveTo((float) arc.getStartPoint().getX(),
			(float) arc.getStartPoint().getY());
            path.append(arc.getPathIterator(null), false);
            path.append(arc2.getPathIterator(null), true);
            path.closePath();
            Line2D separator = new Line2D.Double(arc2.getEndPoint(), arc.getStartPoint());

            if (currentPass == 0) {
                Paint shadowPaint = getShadowPaint();
                double shadowXOffset = getShadowXOffset();
                double shadowYOffset = getShadowYOffset();
                if (shadowPaint != null && getShadowGenerator() == null) {
                    Shape shadowArc = 
			ShapeUtilities.createTranslatedShape(path, (float) shadowXOffset,
							     (float) shadowYOffset);
                    g2.setPaint(shadowPaint);
                    g2.fill(shadowArc);
                }
            } else if (currentPass == 1) {
                Paint paint = lookupSectionPaint(key);
                g2.setPaint(paint);
                g2.fill(path);
                Paint outlinePaint = lookupSectionOutlinePaint(key);
                Stroke outlineStroke = lookupSectionOutlineStroke(key);
                if (getSectionOutlinesVisible() && outlinePaint != null 
		    && outlineStroke != null) {
                    g2.setPaint(outlinePaint);
                    g2.setStroke(outlineStroke);
                    g2.draw(path);
                }
		
                // add an entity for the pie section
                if (state.getInfo() != null) {
                    EntityCollection entities = state.getEntityCollection();
                    if (entities != null) {
                        String tip = null;
                        PieToolTipGenerator toolTipGenerator
			    = getToolTipGenerator();
                        if (toolTipGenerator != null) {
                            tip = toolTipGenerator.generateToolTip(dataset, key);
                        }
                        String url = null;
                        PieURLGenerator urlGenerator = getURLGenerator();
                        if (urlGenerator != null) {
                            url = urlGenerator.generateURL(dataset, key, getPieIndex());
                        }
                        PieSectionEntity entity = 
			    new PieSectionEntity(path, dataset,	getPieIndex(), section,
						 key, tip, url);
                        entities.add(entity);
                    }
                }
            } else if (currentPass == 2) {
                if (getSeparatorsVisible()) {
                    g2.setStroke(getSeparatorStroke());
                    g2.setPaint(getSeparatorPaint());
                    g2.draw(separator);
                }
            }
	    if (burstDatasets.get(key) != null) {
		setNextBurstSeparator(getNextBurstLevelLine(separator, 1.0));
		PieDataset burstDataset = (PieDataset) burstDatasets.get(key);
		List bursts = burstDataset.getKeys();
		for (int burst = 0; burst < bursts.size(); burst++) {
		    drawBurstItem(g2, burst, state, currentPass, burstDataset, angle, level + 1);
		}
	    }
        }
        state.setLatestAngle(angle2);
	setLatestBurstAngle(angle2);
    }

    /**
     * Draws a single burst level data item.
     *
     * @param g2  the graphics device (<code>null</code> not permitted).
     * @param section  the section index.
     * @param state  state information for one chart.
     * @param currentPass  the current pass index.
     * @param currentPass  the current pass index.
     * @param dataset  the burst dataset.
     * @param dataset  the burst dataset.
     * @param parentAngle  the parent section angle.
     * @param level  the burst level.
     */
    protected void drawBurstItem(Graphics2D g2, int section, PiePlotState state,
				 int currentPass, PieDataset dataset, double parentAngle2,
				 int level) {
        Number n = dataset.getValue(section);
        if (n == null) {
            return;
        }
	double totalValue = DatasetUtilities.calculatePieDatasetTotal(dataset);
        double value = n.doubleValue();
        double angle1 = 0.0;
        double angle2 = 0.0;

        Rotation direction = getDirection();
        if (direction == Rotation.CLOCKWISE) {
            angle1 = getLatestBurstAngle();
            angle2 = angle1 - value / totalValue * Math.abs(parentAngle2);
        } else if (direction == Rotation.ANTICLOCKWISE) {
            angle1 = getLatestBurstAngle();
            angle2 = angle1 + value / totalValue * Math.abs(parentAngle2);
        } else {
            throw new IllegalStateException("Rotation type not recognised.");
        }

        double angle = (angle2 - angle1);

        if (Math.abs(angle) > getMinimumArcAngleToDraw()) {
            Comparable key = dataset.getKey(section);
            Rectangle2D burstArcBounds = getBurstArcBounds(state, getNextBurstSeparator(), angle1, angle);
            Arc2D.Double arc = new Arc2D.Double(burstArcBounds, angle1, angle, Arc2D.OPEN);
            // create the bounds for the inner arc
            double depth = getSectionDepth() / (1.9 + (level * 0.1));
            RectangleInsets s = 
		new RectangleInsets(UnitType.RELATIVE, depth, depth, depth, depth);
            Rectangle2D innerArcBounds = new Rectangle2D.Double();
            innerArcBounds.setRect(burstArcBounds);
            s.trim(innerArcBounds);
            // calculate inner arc in reverse direction, for later
            // GeneralPath construction
            Arc2D.Double arc2 = new Arc2D.Double(innerArcBounds, angle1
                    + angle, -angle, Arc2D.OPEN);
            GeneralPath path = new GeneralPath();
            path.moveTo((float) arc.getStartPoint().getX(),
                    (float) arc.getStartPoint().getY());
            path.append(arc.getPathIterator(null), false);
            path.append(arc2.getPathIterator(null), true);
            path.closePath();
	    Line2D separator =  new Line2D.Double(arc2.getEndPoint(), arc.getStartPoint());
	    // ShapeUtilities.rotateShape((Shape) getNextBurstSeparator(), angle2, (float) state.getPieCenterX(), (float) state.getPieCenterY());
	    setNextBurstSeparator(separator);

            if (currentPass == 1) {
                Paint paint = lookupSectionPaint(key);
                g2.setPaint(paint);
                g2.fill(path);
                Paint outlinePaint = lookupSectionOutlinePaint(key);
                Stroke outlineStroke = lookupSectionOutlineStroke(key);
                if (getSectionOutlinesVisible() && outlinePaint != null 
                        && outlineStroke != null) {
                    g2.setPaint(outlinePaint);
                    g2.setStroke(outlineStroke);
                    g2.draw(path);
                }

                // add an entity for the pie section
                if (state.getInfo() != null) {
                    EntityCollection entities = state.getEntityCollection();
                    if (entities != null) {
                        String tip = null;
                        PieToolTipGenerator toolTipGenerator
                                = getToolTipGenerator();
                        if (toolTipGenerator != null) {
                            tip = toolTipGenerator.generateToolTip(dataset,
                                    key);
                        }
                        String url = null;
                        PieURLGenerator urlGenerator = getURLGenerator();
                        if (urlGenerator != null) {
                            url = urlGenerator.generateURL(dataset, key,
                                    getPieIndex());
                        }
                        PieSectionEntity entity = new PieSectionEntity(path,
                                dataset, getPieIndex(), section, key, tip,
                                url);
                        entities.add(entity);
                    }
                }
            } else if (currentPass == 2) {
                if (getSeparatorsVisible()) {
                    g2.setStroke(getSeparatorStroke());
                    g2.setPaint(getSeparatorPaint());
                    g2.draw(separator);
                }
            }
	    if (burstDatasets.get(key) != null) {
		setNextBurstSeparator(getNextBurstLevelLine(separator, 1.0));
		PieDataset burstDataset = (PieDataset) burstDatasets.get(key);
		List bursts = burstDataset.getKeys();
		for (int burst = 0; burst < bursts.size(); burst++) {
		    drawBurstItem(g2, burst, state, currentPass, burstDataset, angle, level + 1);
		}
	    }
        }
	setLatestBurstAngle(angle2);
    }
    
    /**
     * Get the bounding rectangle for the burst level data item.
     *
     * @param state  state information for one chart.
     * @param separator  the burst data items first line. 
     * @param angle  the angle of the first line.
     * @param extent  the extent (angle) of the burst data item.
     */
    protected Rectangle2D getBurstArcBounds(PiePlotState state, Line2D separator, double angle, double extent) {
	Point2D centerPoint = new Point2D.Double(state.getPieCenterX(), state.getPieCenterY());
	Arc2D arc1 = new Arc2D.Double();
	arc1.setArcByCenter(state.getPieCenterX(), state.getPieCenterY(), separator.getP2().distance(centerPoint), angle, extent, Arc2D.OPEN);
        Rectangle2D bounds = arc1.getBounds();
	bounds.createUnion(separator.getBounds());
        return bounds;
    }

    private Line2D getNextBurstLevelLine(Line2D line,
                              double endPercent) {
        ParamChecks.nullNotPermitted(line, "line");
        double x1 = line.getX1();
        double x2 = line.getX2();
        double deltaX = x2 - x1;
        double y1 = line.getY1();
        double y2 = line.getY2();
        double deltaY = y2 - y1;
        x1 = x2 + (endPercent * deltaX);
        y1 = y2 + (endPercent * deltaY);
        return new Line2D.Double(x2, y2, x1, y1);
    }


    private double getDistance2D(Point2D p1, Point2D p2) {
        ParamChecks.nullNotPermitted(p1, "p1");
        ParamChecks.nullNotPermitted(p2, "p2");
        double deltaX = p1.getX() - p2.getX();
        double deltaY = p1.getY() - p2.getY();
	return Math.sqrt(Math.pow(deltaX, 2.0) + Math.pow(deltaY, 2.0));
    }


    /**
     * Tests this plot for equality with an arbitrary object.
     *
     * @param obj  the object to test against (<code>null</code> permitted).
     *
     * @return A boolean.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SunburstPlot)) {
            return false;
        }
        SunburstPlot that = (SunburstPlot) obj;
        if (this.getSeparatorsVisible() != that.getSeparatorsVisible()) {
            return false;
        }
        if (!ObjectUtilities.equal(this.getSeparatorStroke(),
				   that.getSeparatorStroke())) {
            return false;
        }
        if (!PaintUtilities.equal(this.getSeparatorPaint(), that.getSeparatorPaint())) {
            return false;
        }
        if (this.getSectionDepth() != that.getSectionDepth()) {
            return false;
        }
        return super.equals(obj);
    }

    /**
     * Provides serialization support.
     *
     * @param stream  the output stream.
     *
     * @throws IOException  if there is an I/O error.
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
        SerialUtilities.writeStroke(this.getSeparatorStroke(), stream);
        SerialUtilities.writePaint(this.getSeparatorPaint(), stream);
    }

    /**
     * Provides serialization support.
     *
     * @param stream  the input stream.
     *
     * @throws IOException  if there is an I/O error.
     * @throws ClassNotFoundException  if there is a classpath problem.
     */
    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        this.setSeparatorStroke(SerialUtilities.readStroke(stream));
        this.setSeparatorPaint(SerialUtilities.readPaint(stream));
    }

}
