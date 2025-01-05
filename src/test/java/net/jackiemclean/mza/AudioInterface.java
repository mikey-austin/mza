package net.jackiemclean.mza;


public interface AudioInterface {
  void link(Source source, Zone zone);

  void sync(Zone zone, ZoneState zoneState);
}
