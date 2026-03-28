/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.ATOM_GATEWAY_URL || 'http://localhost:8081'}/api/:path*`,
      },
    ]
  },
}

export default nextConfig
