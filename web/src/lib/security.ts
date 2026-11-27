/**
 * Compatible avec AccessCodeSecurity.kt de l'application Android :
 * PBKDF2-HMAC-SHA256, 100 000 itérations, clé 256 bits, sel 16 octets,
 * condensat hexadécimal minuscule. Les codes ne sont jamais stockés en clair.
 */

const ITERATIONS = 100_000;
const KEY_LENGTH_BITS = 256;
const SALT_BYTES = 16;
const CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

export interface HashedAccessCode {
  hashHex: string;
  saltHex: string;
}

export const normalizeCode = (raw: string) => raw.trim().toUpperCase();

export function isValidCodeFormat(raw: string): boolean {
  const clean = normalizeCode(raw);
  return clean.length >= 4 && clean.length <= 16 && /^[a-zA-Z0-9]+$/.test(clean);
}

export function generateAccessCode(length = 6): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => CODE_ALPHABET[byte % CODE_ALPHABET.length]).join("");
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function fromHex(hex: string): Uint8Array | null {
  if (hex.length % 2 !== 0) return null;
  const bytes = new Uint8Array(hex.length / 2);
  for (let index = 0; index < bytes.length; index += 1) {
    const value = Number.parseInt(hex.slice(index * 2, index * 2 + 2), 16);
    if (Number.isNaN(value)) return null;
    bytes[index] = value;
  }
  return bytes;
}

async function pbkdf2(code: string, salt: Uint8Array): Promise<Uint8Array> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(normalizeCode(code)),
    "PBKDF2",
    false,
    ["deriveBits"]
  );
  const saltBuffer = salt.buffer.slice(
    salt.byteOffset,
    salt.byteOffset + salt.byteLength
  ) as ArrayBuffer;
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: saltBuffer, iterations: ITERATIONS },
    key,
    KEY_LENGTH_BITS
  );
  return new Uint8Array(bits);
}

export async function hashAccessCode(rawCode: string): Promise<HashedAccessCode> {
  if (!isValidCodeFormat(rawCode)) {
    throw new Error("Le code d'accès doit contenir entre 4 et 16 caractères alphanumériques.");
  }
  const salt = new Uint8Array(SALT_BYTES);
  crypto.getRandomValues(salt);
  return { hashHex: toHex(await pbkdf2(rawCode, salt)), saltHex: toHex(salt) };
}

function constantTimeEquals(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let index = 0; index < a.length; index += 1) diff |= a[index] ^ b[index];
  return diff === 0;
}

export async function verifyAccessCode(
  rawInput: string,
  storedHashHex: string,
  storedSaltHex: string
): Promise<boolean> {
  const clean = normalizeCode(rawInput);
  if (!clean || !storedHashHex || !storedSaltHex) return false;
  const salt = fromHex(storedSaltHex);
  const expected = fromHex(storedHashHex);
  if (!salt || !expected) return false;
  return constantTimeEquals(await pbkdf2(clean, salt), expected);
}
