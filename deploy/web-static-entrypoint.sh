#!/bin/sh
set -eu

case "${WEB_SLOT:-}" in
  blue|green) ;;
  *) echo 'WEB_SLOT must be blue or green' >&2; exit 2 ;;
esac

rm -f "/run/web-sockets/$WEB_SLOT.sock"
sed "s/@WEB_SLOT@/$WEB_SLOT/g" /etc/nginx/static.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
