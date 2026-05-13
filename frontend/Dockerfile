FROM nginx:alpine

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy static files
COPY src/ /usr/share/nginx/html/

EXPOSE 8631

CMD ["nginx", "-g", "daemon off;"]
