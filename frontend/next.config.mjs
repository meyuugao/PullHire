/** @type {import('next').NextConfig} */
const nextConfig = {
  // Static export is enough for the S0 stub; real SPA screens arrive in S10-S11.
  output: "export",
  images: { unoptimized: true },
};

export default nextConfig;
