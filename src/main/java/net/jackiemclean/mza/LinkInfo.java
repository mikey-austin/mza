package net.jackiemclean.mza;

/** A link between two ports as reported by pw-dump. */
public record LinkInfo(int linkId, int inNodeId, int inPortId, int outNodeId, int outPortId) {
}
