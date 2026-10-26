package com.fcareer.development.track;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GTR-a: anlık görüntünün kompakt ikili biçimi (format 1), gzip'li. Oyuncu başına: kimlik (long), takım (int), yaş, ana mevki (UTF), OVR, POT, form,
 * lig maçı, takım maçı, maç sayısı ve attribute'lar (sayı + değerler). Bilinmeyen alan -1 (UTF'de boş metin).
 */
public final class GrowthSnapshotCodec {

    private static final int UNKNOWN = TrackedPlayer.UNKNOWN;

    private GrowthSnapshotCodec() {
    }

    public static byte[] encode(List<TrackedPlayer> players) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(players.size());
            for (TrackedPlayer p : players) {
                out.writeLong(p.playerId());
                out.writeInt(orUnknown(p.teamId()));
                out.writeByte(orUnknown(p.age()));
                out.writeUTF(Objects.requireNonNullElse(p.position(), ""));
                out.writeByte(p.overall());
                out.writeByte(orUnknown(p.potential()));
                out.writeByte(orUnknown(p.form()));
                out.writeShort(orUnknown(p.leagueApps()));
                out.writeShort(orUnknown(p.teamMatches()));
                out.writeShort(orUnknown(p.matches()));
                out.writeByte(p.attrs().length);
                for (int v : p.attrs()) {
                    out.writeByte(v);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static List<TrackedPlayer> decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            int count = in.readInt();
            List<TrackedPlayer> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long id = in.readLong();
                Integer team = known(in.readInt());
                Integer age = known(in.readByte());
                String position = in.readUTF();
                int overall = in.readByte();
                Integer potential = known(in.readByte());
                Integer form = known(in.readByte());
                Integer apps = known(in.readShort());
                Integer teamMatches = known(in.readShort());
                Integer matches = known(in.readShort());
                int[] attrs = new int[in.readByte()];
                for (int a = 0; a < attrs.length; a++) {
                    attrs[a] = in.readByte();
                }
                out.add(new TrackedPlayer(id, team, age, position.isEmpty() ? null : position, overall, potential, form, apps, teamMatches, matches, attrs));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int orUnknown(Integer value) {
        return Objects.isNull(value) ? UNKNOWN : value;
    }

    private static Integer known(int value) {
        return value == UNKNOWN ? null : value;
    }
}
