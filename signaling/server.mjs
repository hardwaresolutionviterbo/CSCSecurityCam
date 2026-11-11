#!/usr/bin/env node
/**
 * Relay di signaling minimale per CSCSecurityCam.
 *
 * Non tocca il video: inoltra solo SDP e candidati ICE tra la telecamera e il centro di controllo
 * che entrano nella stessa stanza. Una stanza = un codice `CSC-xxxx-xxxx`.
 *
 * Avvio locale:   npm install && npm start
 * Dietro TLS:     mettilo dietro un reverse proxy (Caddy/Nginx) e usa wss:// nel telefono,
 *                 oppure `wss://` terminato dal proxy: l'app rifiuta ws:// fuori da localhost.
 */
import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8787);

/** room -> Map(role -> socket) */
const rooms = new Map();

function peerOf(room, role) {
  const peers = rooms.get(room);
  if (!peers) return null;
  return peers.get(role === 'camera' ? 'viewer' : 'camera') || null;
}

function forEachPeer(room, fn) {
  const peers = rooms.get(room);
  if (!peers) return;
  for (const socket of peers.values()) fn(socket);
}

const wss = new WebSocketServer({ port: PORT });
console.log(`CSC signaling relay in ascolto su ws://0.0.0.0:${PORT}`);

wss.on('connection', (ws) => {
  let room = null;
  let role = null;

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }
    if (!msg || typeof msg.type !== 'string') return;

    if (msg.type === 'join') {
      if (typeof msg.room !== 'string' || !msg.room.trim()) {
        ws.send(JSON.stringify({ type: 'error', message: 'Stanza mancante' }));
        return;
      }
      if (msg.role !== 'camera' && msg.role !== 'viewer') {
        ws.send(JSON.stringify({ type: 'error', message: 'Ruolo non valido' }));
        return;
      }
      room = msg.room.trim();
      role = msg.role;

      if (!rooms.has(room)) rooms.set(room, new Map());
      const peers = rooms.get(room);
      const previous = peers.get(role);
      if (previous && previous !== ws) previous.close(1000, 'sostituito');

      peers.set(role, ws);
      console.log(`[${room}] ${role} connesso (${peers.size}/2)`);

      ws.send(JSON.stringify({ type: 'peer-joined', role: 'self' }));
      forEachPeer(room, (socket) => {
        if (socket !== ws) socket.send(JSON.stringify({ type: 'peer-joined', role }));
      });
      return;
    }

    if (!room) return;

    // sdp / ice / bye: inoltro puro all'altro partecipante della stanza.
    forEachPeer(room, (socket) => {
      if (socket !== ws) socket.send(JSON.stringify(msg));
    });
  });

  ws.on('close', () => {
    if (!room) return;
    const peers = rooms.get(room);
    if (!peers) return;
    if (peers.get(role) === ws) peers.delete(role);
    forEachPeer(room, (socket) => {
      socket.send(JSON.stringify({ type: 'peer-left', role }));
    });
    if (peers.size === 0) rooms.delete(room);
    console.log(`[${room}] ${role} disconnesso`);
  });
});
