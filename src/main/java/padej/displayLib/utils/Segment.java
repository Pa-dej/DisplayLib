package padej.displayLib.utils;

import org.bukkit.Location;

public class Segment {
    private final Location pointA;
    private final Location pointB;

    public Segment(Location pointA, Location pointB) {
        this.pointA = pointA;
        this.pointB = pointB;
    }

    public double length() {
        return pointA.distance(pointB);
    }

    public Location getMidpoint() {
        double midX = (pointA.getX() + pointB.getX()) / 2;
        double midY = (pointA.getY() + pointB.getY()) / 2;
        double midZ = (pointA.getZ() + pointB.getZ()) / 2;
        return new Location(pointA.getWorld(), midX, midY, midZ);
    }

    public Location[] getMinMaxPoints() {
        double minX = java.lang.Math.min(pointA.getX(), pointB.getX());
        double maxX = java.lang.Math.max(pointA.getX(), pointB.getX());
        double minY = java.lang.Math.min(pointA.getY(), pointB.getY());
        double maxY = java.lang.Math.max(pointA.getY(), pointB.getY());
        double minZ = java.lang.Math.min(pointA.getZ(), pointB.getZ());
        double maxZ = java.lang.Math.max(pointA.getZ(), pointB.getZ());

        Location minPoint = new Location(pointA.getWorld(), minX, minY, minZ);
        Location maxPoint = new Location(pointA.getWorld(), maxX, maxY, maxZ);

        return new Location[]{minPoint, maxPoint};
    }

    public Location getPointOnSegment(double t) {
        double x = pointA.getX() + (pointB.getX() - pointA.getX()) * t;
        double y = pointA.getY() + (pointB.getY() - pointA.getY()) * t;
        double z = pointA.getZ() + (pointB.getZ() - pointA.getZ()) * t;
        return new Location(pointA.getWorld(), x, y, z);
    }
}