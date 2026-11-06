import { useState } from 'react';
import type { Book } from './types';
import classics from './data/classics.json';
const palettes = [
  ['#e6c958', '#242c2a', '#2b5550', '#f4e6af'],
  ['#264b55', '#f7eee0', '#e5aa70', '#89b5b9'],
  ['#e9e3d7', '#2b3935', '#567e65', '#acc4af'],
  ['#a74537', '#fff2d6', '#efb76d', '#6d302e'],
  ['#303c70', '#f4ead5', '#b9c3d9', '#dc9b7b'],
  ['#e6b9b2', '#422d35', '#78516a', '#f6dbbd'],
  ['#2b4536', '#f2e7c8', '#a7b784', '#e4b96d'],
  ['#ddd9eb', '#373449', '#7c779e', '#b0bed3'],
];
export function BookCover({ book, large = false }: { book: Book; large?: boolean }) {
  const [broken, setBroken] = useState('');
  const classic = classics.find(
    (entry) => entry.title === book.title && entry.author === book.author,
  );
  const src = book.cover || (classic ? `/covers/${classic.id}.jpg` : '');
  if (src && broken !== src)
    return (
      <img
        className={`cover classic-shelf-cover ${large ? 'large' : ''}`}
        src={src}
        alt=""
        onError={() => setBroken(src)}
      />
    );
  let hash = 0;
  for (const char of book.id || book.title) hash = (Math.imul(31, hash) + char.charCodeAt(0)) | 0;
  const seed = hash >>> 1;
  const [paper, ink, field, fold] = palettes[seed % palettes.length];
  const title =
    book.title
      .trim()
      .replace(/\.(pdf|epub|docx|pptx|txt|md)$/i, '')
      .replace(/_/g, ' ')
      .replace(/\s+/g, ' ')
      .slice(0, 280) || 'Untitled document';
  return (
    <svg
      className={`cover generated-cover ${large ? 'large' : ''}`}
      viewBox="0 0 400 600"
      aria-hidden="true"
      style={{ background: paper, color: ink }}
    >
      <rect width="400" height="600" fill={paper} />
      {seed % 3 === 0 ? (
        <>
          <path fill={field} d="M36 382h204v144H36zM254 382a110 144 0 0 1 0 288z" />
          <path fill={fold} d="M240 382h124v144H240z" />
          <path fill={paper} d="M240 382a60 144 0 0 1 0 288z" />
        </>
      ) : seed % 3 === 1 ? (
        <>
          <ellipse fill={field} cx="180" cy="454" rx="144" ry="72" />
          <path fill={fold} d="M200 382h164v144H200z" />
          <path fill={paper} d="M200 382h8v144h-8z" />
        </>
      ) : (
        <>
          <path fill={field} d="M36 382h328v144H36z" />
          <path fill={fold} d="m36 526 328-144v144z" />
          <path fill={paper} d="M192 382h8v144h-8z" />
        </>
      )}
      <foreignObject x="36" y="38" width="328" height="310">
        <div className="generated-cover-copy" style={{ color: ink }}>
          <div
            className="generated-cover-title"
            style={{ fontSize: title.length > 120 ? 30 : title.length > 75 ? 38 : 52 }}
          >
            {title}
          </div>
          {book.author && <div className="generated-cover-author">{book.author.slice(0, 120)}</div>}
        </div>
      </foreignObject>
      <text
        x="36"
        y="566"
        fill={ink}
        fontFamily="Outfit, sans-serif"
        fontSize="17"
        fontWeight="550"
      >
        {book.format.toUpperCase().slice(0, 18)}
      </text>
      <text
        x="364"
        y="566"
        fill={ink}
        fontFamily="Outfit, sans-serif"
        fontSize="17"
        fontWeight="550"
        textAnchor="end"
      >
        Vern
      </text>
    </svg>
  );
}
